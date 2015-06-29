package fr.lteconsulting;

import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.ThreadReceivePort;

/**
 * Hello world!
 *
 */
public class App
{
	public static void main( String[] args )
	{
		Spy master = new Spy( "master" );
		master.start();

		Spy spy = new Spy( "spy", master );
		spy.start();

		Channel receive = spy.send( "toto", null );

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

		// spy.send( "titi", null );
		// spy.send( "tata", null );

		System.out.println();
	}
}
