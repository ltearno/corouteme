package fr.lteconsulting;

import java.util.concurrent.ExecutionException;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.ThreadReceivePort;

import com.offbynull.coroutines.user.CoroutineRunner;

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
		System.out.println( "fufub" );

		final Channel<Integer> ch = Channels.newChannel( 0 );

		new Fiber<Void>( ( ) -> {
			for( int i = 0; i < 10; i++ )
			{
				Strand.sleep( 100 );
				ch.send( i );
			}
			ch.close();
		} ).start();

		try
		{
			new Fiber<Void>( ( ) -> {
				Integer x;
				while( (x = ch.receive()) != null )
					System.out.println( "--> " + x );
			} ).start().join();
		}
		catch( ExecutionException | InterruptedException e )
		{
			System.out.println( "errrro" );
		}

		System.out.println( "Hello my worlds !" );
		CoroutineRunner r = new CoroutineRunner( new MyCoroutine() );
		r.execute();
		r.execute();
		r.execute();
		r.execute();
	}
}
