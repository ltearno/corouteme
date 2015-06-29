package fr.lteconsulting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.SelectAction;
import co.paralleluniverse.strands.channels.Selector;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * Spy:
 * <p/>
 * - boucle de message - un message correspond a un appel RPC - une réponse est
 * renvoyée - => il est possible de wrapper une interface
 * <p/>
 * - un Channel de réception des messages - un getter d'interface nommée
 * (facades)
 */
public abstract class Spy
{
	private final String surname;

	private Channel<SpyMessage> msgChannel;

	private Fiber<Integer> fiber;

	private final List<MessageProcessingContinuation> messageProcessings = new ArrayList<>();

	abstract protected void startUp();

	abstract protected Object processMessage( SpyMessage message, SpyCaller spyCaller, Continuation continuation, MessageProcessingContinuation messageProcessingContinuation );

	public interface SpyCaller
	{
		Object callSpy( Spy spy, String methodName, Object[] parameters );
	}

	class MessageProcessingContinuation
	{
		private final SpyMessage message;

		private Channel<Object> waitingChannel;

		private final CoroutineRunner runner;

		private Object receivedObject;

		public MessageProcessingContinuation( SpyMessage message )
		{
			this.message = message;
			this.runner = new CoroutineRunner( coroutine );
		}

		public boolean step()
		{
			return runner.execute();
		}

		public Coroutine getCoroutine()
		{
			return coroutine;
		}

		public Channel<Object> getResponseChannel()
		{
			return waitingChannel;
		}

		private class SpyCallerCoroutine implements SpyCaller
		{
			Continuation continuation;

			@Override
			public Object callSpy( Spy spy, String methodName, Object[] parameters )
			{
				return MessageProcessingContinuation.this.callSpy( continuation, spy, methodName, parameters );
			}
		}

		private Coroutine coroutine = new Coroutine()
		{
			@Override
			public void run( Continuation continuation ) throws Exception
			{
				// Process the message
				log( "Start of the continuation, processing message " + message );

				SpyCallerCoroutine spyCaller = new SpyCallerCoroutine();
				spyCaller.continuation = continuation;
				Object result = processMessage( message, spyCaller, continuation, MessageProcessingContinuation.this );

				// now return something to the caller
				Channel<Object> responseChannel = message.getResponseChannel();
				log( "Finished processing message with result '" + result + "', sending answer to " + System.identityHashCode( responseChannel ) );
				try
				{
					responseChannel.send( result );
				}
				catch( Exception e )
				{
					e.printStackTrace();
				}
				log( "Finished process message, REAL" );

				messageProcessings.remove( MessageProcessingContinuation.this );
			}
		};

		protected Object callSpy( Continuation continuation, Spy spy, String methodName, Object[] parameters )
		{
			assert waitingChannel == null : "responseChannel should be null by now !";

			receivedObject = null;
			waitingChannel = Channels.newChannel( -1 );

			try
			{
				spy.msgChannel.send( new SpyMessage( methodName, parameters, waitingChannel ) );
				log( "Suspending continuation..." + System.identityHashCode( continuation ) + " on wait for channel " + System.identityHashCode( waitingChannel ) );

				continuation.suspend();

				log( "Continuation resumed" + System.identityHashCode( continuation ) + " result is = " + receivedObject );
				Object result = receivedObject;

				return result;
			}
			catch( SuspendExecution e )
			{
				log( e.getMessage() );
				e.printStackTrace();
				throw new RuntimeException( e );
			}
			catch( InterruptedException e )
			{
				log( e.getMessage() );
				e.printStackTrace();
				throw new RuntimeException( e );
			}
			finally
			{
				receivedObject = null;
				waitingChannel.close();
				waitingChannel = null;
			}

			// return null;
		}
	}

	protected void log( String message )
	{
		System.out.println( surname + " : " + message );
	}

	public Spy( String surname )
	{
		this.surname = surname;
	}

	private boolean executing;

	private Integer fiberExecution() throws SuspendExecution
	{
		assert !executing : "already executing !";
		if( executing )
			throw new RuntimeException( "already executing !" );
		executing = true;

		log( "start message loop" );
		startUp();

		while( true )
		{
			pumpMessage();
		}

		// msgChannel.close();

		// return 0;
	}

	@Suspendable
	private void pumpMessage() throws SuspendExecution
	{
		try
		{
			log( "prepare pumping" );

			HashMap<Integer, MessageProcessingContinuation> spyBySelect = new HashMap<>();

			List<SelectAction<Object>> actions = new ArrayList<>();
			actions.add( Selector.receive( msgChannel ) );
			for( MessageProcessingContinuation spy : messageProcessings )
			{
				if( spy.waitingChannel == null )
					continue;

				SelectAction<Object> action = Selector.receive( spy.waitingChannel );
				actions.add( action );
				spyBySelect.put( System.identityHashCode( action ), spy );
			}

			log( "SELECTING " + actions.size() + " ACTIONS" );

			SelectAction<Object> selected = Selector.select( actions );

			if( selected == null )
			{
				log( "NOTHING SELECTED !!!" );
				return;
			}

			log( "SELECTION ON CHANNEL " + System.identityHashCode( selected.port() ) );

			if( (Object) selected.port() == (Object) msgChannel )
			{
				log( "RECEIVED A MESSAGE TO PROCESS " + selected.message() );

				MessageProcessingContinuation messageProcessing = new MessageProcessingContinuation( (SpyMessage) selected.message() );
				messageProcessings.add( messageProcessing );

				messageProcessing.step();
			}
			else
			{
				log( "FINISHED AN IO OPERATION, CONTIUING A SPY" );

				MessageProcessingContinuation waitingmessageProcessing = spyBySelect.get( System.identityHashCode( selected ) );
				if( waitingmessageProcessing == null )
				{
					log( "WEIRD SELECTED OPERATION BUT NON WAITING SPY ON IT !" );
					return;
				}

				waitingmessageProcessing.receivedObject = selected.message();
				waitingmessageProcessing.step();
			}

			log( "finished pump loop" );
		}
		catch( InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * TO BE CALLED BY EXTERNAL THREADS
	 * 
	 * @param methodName
	 * @param parameters
	 * @return
	 */
	public Channel send( String methodName, Object[] parameters )
	{
		try
		{
			Channel c = Channels.newChannel( -1 );
			msgChannel.send( new SpyMessage( methodName, parameters, c ) );
			return c;
		}
		catch( SuspendExecution | InterruptedException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	public void start()
	{
		msgChannel = Channels.newChannel( -1 );
		fiber = new Fiber<Integer>( this::fiberExecution );
		log( "fiber = " + fiber.getName() + ", channel = " + System.identityHashCode( msgChannel ) );

		fiber.start();
	}
}

class SpyMessage
{
	private final String methodName;

	private final Object[] parameters;

	private final Channel<Object> responseChannel;

	SpyMessage( String methodName, Object[] parameters, Channel<Object> responseChannel )
	{
		this.methodName = methodName;
		this.parameters = parameters;
		this.responseChannel = responseChannel;
	}

	public String getMethodName()
	{
		return methodName;
	}

	public Object[] getParameters()
	{
		return parameters;
	}

	public Channel<Object> getResponseChannel()
	{
		return responseChannel;
	}

	@Override
	public String toString()
	{
		StringBuilder res = new StringBuilder();
		res.append( "[MESSAGE|" );
		res.append( methodName );
		res.append( "(" );
		if( parameters != null )
		{
			for( int i = 0; i < parameters.length; i++ )
			{
				if( i > 0 )
					res.append( ", " );
				res.append( "" + parameters[i] );
			}
		}
		res.append( ")" );
		if( responseChannel == null )
			res.append( " *no_resp_channel*" );
		res.append( "]" );
		return res.toString();
	}
}
