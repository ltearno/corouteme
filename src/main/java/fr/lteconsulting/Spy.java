package fr.lteconsulting;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import de.matthiasmann.continuations.Coroutine;
import de.matthiasmann.continuations.Coroutine.State;
import de.matthiasmann.continuations.CoroutineProto;

/**
 * Spy class
 * 
 * a bit like an agent, but communicates in the underground to get answers
 */
public abstract class Spy
{
	private final String surname;

	private Channel<Object> msgChannel;

	private Fiber<Integer> fiber;

	abstract protected void startUp();

	abstract protected Object processMessage( SpyCallMessage message, Continuation ctx ) throws de.matthiasmann.continuations.SuspendExecution;

	public Spy( String surname )
	{
		this.surname = surname;
	}

	public void start()
	{
		start( -1 );
	}

	public void start( int channelSize )
	{
		msgChannel = Channels.newChannel( channelSize );
		fiber = new Fiber<Integer>( this::fiberExecution );
		debug( "fiber = " + fiber.getName() + ", channel = " + System.identityHashCode( msgChannel ) );

		fiber.start();
	}

	/**
	 * TO BE CALLED BY EXTERNAL THREADS
	 * 
	 * @param methodName
	 * @param parameters
	 * @return
	 */
	public void send( String methodName, Object[] parameters, Channel<Object> resultChannel )
	{
		try
		{
			msgChannel.send( new SpyCallMessage( methodName, parameters, resultChannel, null ) );
		}
		catch( SuspendExecution | InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	protected void debug( String message )
	{
		//System.out.println( "[" + Thread.currentThread().getId() + "] " + surname + " : " + message );
	}

	protected void log( String message )
	{
		//System.out.println( "[" + Thread.currentThread().getId() + "] " + surname + " : " + message );
	}

	abstract class Continuation
	{
		private Object receivedObject;
		private final Coroutine runner;

		@Suspendable
		abstract protected void run() throws de.matthiasmann.continuations.SuspendExecution;

		protected Continuation()
		{
			this.runner = new Coroutine( coroutine );
		}

		/**
		 * returns true when finished
		 * 
		 * @return
		 */
		@Suspendable
		public boolean step()
		{
			runner.run();
			return runner.getState() == State.FINISHED;
		}

		public CoroutineProto getCoroutine()
		{
			return coroutine;
		}

		private CoroutineProto coroutine = new CoroutineProto()
		{
			@Override
			@Suspendable
			public void coExecute() throws de.matthiasmann.continuations.SuspendExecution
			{
				run();
			}
		};

		@Suspendable
		protected Object callSpy( Spy spy, String methodName, Object[] parameters ) throws de.matthiasmann.continuations.SuspendExecution
		{
			receivedObject = null;

			try
			{
				spy.msgChannel.send( new SpyCallMessage( methodName, parameters, msgChannel, Continuation.this ) );
				debug( "Suspending continuation and wait on channel " + System.identityHashCode( msgChannel ) );

				Coroutine.yield();

				Object result = receivedObject;

				debug( "Continuation resumed result is = " + result );

				receivedObject = null;

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

	class MessageProcessingContinuation extends Continuation
	{
		private final SpyCallMessage message;

		public MessageProcessingContinuation( SpyCallMessage message )
		{
			this.message = message;
		}

		@Suspendable
		@Override
		protected void run() throws de.matthiasmann.continuations.SuspendExecution
		{
			debug( "Start of the continuation, processing message " + message );

			Object result = processMessage( message, MessageProcessingContinuation.this );

			// now return something to the caller
			Channel<Object> responseChannel = message.getResponseChannel();
			if( responseChannel == null )
			{
				debug( "no response channel for this message !" );
			}
			else
			{
				debug( "Sending result '" + result + "' on channel " + System.identityHashCode( responseChannel ) );
				try
				{
					responseChannel.send( new SpyResponseMessage( result, message.getCookie() ) );
				}
				catch( Exception e )
				{
					log( e.getMessage() );
					e.printStackTrace();
				}
			}
			debug( "Finished process message" );
		}
	}

	private boolean executing;

	private Integer fiberExecution() throws SuspendExecution
	{
		assert !executing : "already executing !";
		if( executing )
			throw new RuntimeException( "already executing !" );
		executing = true;

		debug( "initiate spy" );
		Continuation startUpContinuation = new Continuation()
		{
			@Override
			protected void run() throws de.matthiasmann.continuations.SuspendExecution
			{
				startUp();
			}
		};
		while( !startUpContinuation.step() )
			;

		debug( "start message loop" );
		while( pumpMessage() )
			;

		return 0;
	}

	private boolean pumpMessage() throws SuspendExecution
	{
		try
		{
			debug( "pumping" );
			Object message = msgChannel.receive();

			Continuation continuation = null;

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

				continuation = (Continuation) respMsg.getCookie();
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
}
