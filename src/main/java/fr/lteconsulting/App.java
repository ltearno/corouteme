package fr.lteconsulting;

import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.ThreadReceivePort;
import de.matthiasmann.continuations.SuspendExecution;

/**
 * Hello world!
 *
 */
public class App
{
	static class Dto
	{
		public int unreplied;
	}

	public static void main( String[] args )
	{
		Spy master = new Spy( "master" )
		{
			@Override
			protected void startUp()
			{
				log("Hello ! I am the first and before, the master");
			}

			@Override
			protected Object processMessage(SpyMessage message, MessageProcessingContinuation ctx)
			{
				log("received a question : " + message);
				String res = "Don't disturb me I said, whatever the " + message;
				return res;
			}
		};
		master.start();

		Dto dto = new Dto();

		Spy printer = new Spy("printer")
		{
			@Override
			protected void startUp()
			{
				log("I am the printer !");
			}

			@Override
			protected Object processMessage(SpyMessage message, MessageProcessingContinuation ctx)
			{
				log(message.toString());
				return null;
			}
		};
		printer.start();

		sleep( 500 );

		for( int i = 0; i < 3; i++ )
		{
			Spy spy = new Spy( "puppet-" + i )
			{
				@Override
				protected void startUp()
				{
					log("I am one of the puppets");
				}

				@Override
				protected Object processMessage(SpyMessage message, MessageProcessingContinuation ctx)
						throws SuspendExecution
				{
					dto.unreplied++;
					log("i've been asked about " + message.getMethodName() + ", i'm going to ask to the master");
					Object result = ctx.callSpy(master, "askAbout", new Object[] { message.getMethodName() });
					dto.unreplied--;
					log("master said " + result);

					ctx.callSpy(printer, "PRINT LOUDLY", new Object[] { result });

					return "master said " + result;
				}
			};
			spy.start();

			// spy.send( "meaning of life", null );
			Channel<Object> receive = spy.send("meaning of life", null);
			ThreadReceivePort<Object> rp = new ThreadReceivePort<>( receive );

			try
			{
				Object object = rp.receive();
				System.out.println( "====> RECEIVED " + object );
			}
			catch( InterruptedException e2 )
			{
				e2.printStackTrace();
			}
		}

		sleep( 3000 );

		System.out.println( "The master left " + dto.unreplied + " questions unreplied..." );

		// spy.send( "titi", null );
		// spy.send( "tata", null );

		System.out.println();
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
