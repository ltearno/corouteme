package fr.lteconsulting;

import java.util.concurrent.ExecutionException;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;

import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * Hello world!
 *
 */
public class App
{
	public static void main(String[] args)
	{
		Spy spy = new Spy();
		spy.start();

		spy.send("toto", null);
		try
		{
			Thread.sleep(1000);
		}
		catch (InterruptedException e1)
		{
			// TODO Gérer l'exception InterruptedException
		}

		System.out.println("fufub");

		final Channel<Integer> ch = Channels.newChannel(0);

      new Fiber<Void>(() -> {
          for (int i = 0; i < 10; i++) {
              Strand.sleep(100);
              ch.send(i);
          }
          ch.close();
      }).start();

		try
		{
			new Fiber<Void>(() -> {
				Integer x;
				while ((x = ch.receive()) != null)
					System.out.println("--> " + x);
			}).start().join();
		}
		catch (ExecutionException | InterruptedException e)
		{
			System.out.println("errrro");
		}
      
		System.out.println("Hello my worlds !");
		CoroutineRunner r = new CoroutineRunner(new MyCoroutine());
		r.execute();
		r.execute();
		r.execute();
		r.execute();
	}
}
