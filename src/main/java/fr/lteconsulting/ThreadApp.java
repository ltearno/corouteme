package fr.lteconsulting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadApp
{
	static AtomicInteger currentId = new AtomicInteger(0);

	static int fuzzyCounter = 0;

	static ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<String, Integer>();

	static synchronized void increment()
	{
		Integer current = map.get("value");
		if(current == null)
			current = 0;
		current++;
		map.put("value", current);
		currentId.incrementAndGet();
	}

	public static void main(String[] args)
	{
		long start = System.nanoTime();

		int nbSpies = 100;

		for (int i = 0; i < nbSpies; i++)
		{
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					while (true)
					{
						increment();
						fuzzyCounter++;
					}
				}
			}).start();
		}

		try
		{
			Thread.currentThread().sleep(5000);
		}
		catch (InterruptedException e)
		{
		}

		long end = System.nanoTime();

		long duration = end - start;

		int nbProcessed = fuzzyCounter;

		double secondsElapsed = duration / 1000000000.0d;
		double speed = nbProcessed / secondsElapsed;

		System.out.println("finished " + nbProcessed + " answers in " + duration / 1000000
				+ " ms speed="
				+ speed + " answers/s.");
	}
}
