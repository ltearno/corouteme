package fr.lteconsulting;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import de.matthiasmann.continuations.Coroutine;
import de.matthiasmann.continuations.CoroutineProto;

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
	private final Object nullMessage = new Object();

	private final String surname;

	private Channel<Object> msgChannel;

	private Fiber<Integer> fiber;

	abstract protected void startUp();

	abstract protected Object processMessage( SpyCallMessage message, MessageProcessingContinuation ctx ) throws de.matthiasmann.continuations.SuspendExecution;

	class MessageProcessingContinuation
	{
		private final SpyCallMessage message;

		private final Coroutine runner;

		private Object receivedObject;

		public MessageProcessingContinuation( SpyCallMessage message )
		{
			this.message = message;
			this.runner = new Coroutine( coroutine );
		}

		public void step()
		{
			runner.run();
		}

		public CoroutineProto getCoroutine()
		{
			return coroutine;
		}

		private CoroutineProto coroutine = new CoroutineProto()
		{
			@Override
			public void coExecute() throws de.matthiasmann.continuations.SuspendExecution
			{
				debug( "Start of the continuation, processing message " + message );

				Object result = processMessage( message, MessageProcessingContinuation.this );

				// now return something to the caller
				Channel<Object> responseChannel = message.getResponseChannel();
				debug( "Sending result '" + result + "' on channel " + System.identityHashCode( responseChannel ) );
				try
				{
					if( result == null )
						result = nullMessage;
					responseChannel.send( new SpyResponseMessage( result, message.getCookie() ) );
				}
				catch( Exception e )
				{
					e.printStackTrace();
				}
				debug( "Finished process message" );
			}
		};

		protected Object callSpy( Spy spy, String methodName, Object[] parameters ) throws de.matthiasmann.continuations.SuspendExecution
		{
			receivedObject = null;

			try
			{
				spy.msgChannel.send( new SpyCallMessage( methodName, parameters, msgChannel, MessageProcessingContinuation.this ) );
				debug( "Suspending continuation and wait on channel " + System.identityHashCode( msgChannel ) );

				Coroutine.yield();

				debug( "Continuation resumed result is = " + receivedObject );

				Object result = receivedObject;
				receivedObject = null;

				if( result == nullMessage )
					result = null;

				return result;
			}
			catch( SuspendExecution e )
			{
				debug( e.getMessage() );
				e.printStackTrace();
				throw new RuntimeException( e );
			}
			catch( InterruptedException e )
			{
				debug( e.getMessage() );
				e.printStackTrace();
				throw new RuntimeException( e );
			}
		}
	}

	protected void debug( String message )
	{
		System.out.println( "[" + Thread.currentThread().getId() + "] " + surname + " : " + message );
	}

	protected void log( String message )
	{
		System.out.println( "[" + Thread.currentThread().getId() + "] " + surname + " : " + message );
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

		debug( "start message loop" );
		startUp();

		while( pumpMessage() )
			;

		return 0;
	}

	@Suspendable
	private boolean pumpMessage() throws SuspendExecution
	{
		try
		{
			debug( "pumping" );
			Object message = msgChannel.receive();

			MessageProcessingContinuation continuation = null;

			if( message instanceof SpyCallMessage )
			{
				SpyCallMessage callMsg = (SpyCallMessage) message;

				debug( "RECEIVED A MESSAGE TO PROCESS " + callMsg );

				continuation = new MessageProcessingContinuation( callMsg );
			}

			else if( message instanceof SpyResponseMessage )
			{
				SpyResponseMessage respMsg = (SpyResponseMessage) message;

				debug( "FINISHED AN IO OPERATION, CONTIUING A SPY " + respMsg );

				continuation = (MessageProcessingContinuation) respMsg.getCookie();
				if( continuation == null )
				{
					debug( "Response message with no continuation associated !" );
					return false;
				}

				continuation.receivedObject = respMsg.getObject();
			}

			if( continuation != null )
				continuation.step();
			else
				log( "no continuation associated with message !" );
			debug( "finished pumpimg" );
		}
		catch( InterruptedException e )
		{
			e.printStackTrace();
		}

		return true;
	}

	/**
	 * TO BE CALLED BY EXTERNAL THREADS
	 * 
	 * @param methodName
	 * @param parameters
	 * @return
	 */
	public Channel<Object> send( String methodName, Object[] parameters )
	{
		try
		{
			Channel<Object> c = Channels.newChannel( -1 );
			msgChannel.send( new SpyCallMessage( methodName, parameters, c, null ) );
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
		debug( "fiber = " + fiber.getName() + ", channel = " + System.identityHashCode( msgChannel ) );

		fiber.start();
	}
}
