package fr.lteconsulting;

import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.ThreadReceivePort;
import de.matthiasmann.continuations.SuspendExecution;

/**
 * Hello world!
 *
 */
public class App
{
	public static void main( String[] args )
	{
		Spy master = new Spy( "master" )
		{
			@Override
			protected void startUp()
			{
				log( "Hello ! I am the first and before, the master" );
			}

			@Override
			protected Object processMessage( SpyCallMessage message, Continuation ctx )
			{
				log( "received a question : " + message );
				String res = "Don't disturb me I said, whatever the " + message;
				return res;
			}
		};
		master.start();

		Spy printer = new Spy( "printer" )
		{
			@Override
			protected void startUp()
			{
				log( "I am the printer !" );
			}

			@Override
			protected Object processMessage( SpyCallMessage message, Continuation ctx )
			{
				//System.out.println( "[" + Thread.currentThread().getId() + "] : " + message );
				log( message.toString() );
				return null;
			}
		};
		printer.start();

		// response channel
		Channel<Object> channel = Channels.newChannel( -1 );

		int nbQuestions = 50000;

		for( int i = 0; i < nbQuestions; i++ )
		{
			Spy spy = new Spy( "puppet-" + i )
			{
				@Override
				protected void startUp()
				{
					log( "I am one of the puppets" );
				}

				@Override
				protected Object processMessage( SpyCallMessage message, Continuation ctx ) throws SuspendExecution
				{
					log( "i've been asked about " + message.getMethodName() + ", i'm going to ask to the master" );
					Object result = ctx.callSpy( master, "askAbout", new Object[] { message.getMethodName() } );
					log( "master said " + result );

					ctx.callSpy( printer, "PRINT LOUDLY", new Object[] { result } );

					return "master said " + result;
				}
			};
			spy.start();

			spy.send( "meaning of life -> " + i, null, channel );
		}

		ThreadReceivePort<Object> rp = new ThreadReceivePort<>( channel );
		try
		{
			while( nbQuestions-- > 0 )
			{
				Object object = rp.receive();
				//System.out.println( "====> RECEIVED (" + nbQuestions + " left) : " + object );
			}
		}
		catch( InterruptedException e2 )
		{
			e2.printStackTrace();
		}
		
		System.out.println("Finished, received all the answers !");
	}

	private static void sleep( int ms )
	{
		try
		{
			Thread.sleep( ms );
		}
		catch( InterruptedException e )
		{
		}
	}
}
