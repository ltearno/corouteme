package fr.lteconsulting;

import java.util.ArrayList;
import java.util.List;

import co.paralleluniverse.actors.Actor;
import co.paralleluniverse.actors.MailboxConfig;
import co.paralleluniverse.actors.behaviors.ProxyServerActor;
import co.paralleluniverse.actors.behaviors.Server;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;

public class SpyApp
{
	@Suspendable
	public interface Master
	{
		int getId();
	}

	public static void main(String[] args)
	{
		long start = System.nanoTime();

		Server masterRef = new ProxyServerActor("name", new MailboxConfig(20, OverflowPolicy.BLOCK), false,
				new Master()
				{
					private int counter = 0;

					@Override
					public int getId()
					{
						return counter++;
					}
				}).spawn();

		Master master = (Master)masterRef;

		int nbSpies = 10;

		class SpyActor extends Actor<Object, Void>
		{
			int receivedAnswers = 0;
			
			public SpyActor(String name)
			{
				super(name, new MailboxConfig(2, OverflowPolicy.BLOCK));
			}

			@Override
			protected Void doRun() throws InterruptedException, SuspendExecution
			{
				while (true)
				{
					int id = master.getId();
					receivedAnswers++;
				}
			}

			public int getReceivedAnswers()
			{
				return receivedAnswers;
			}
		}

		List<SpyActor> spies = new ArrayList<>();
		for (int i = 0; i < nbSpies; i++)
		{
			SpyActor spy = new SpyActor("spy-" + i);
			spies.add(spy);
			spy.spawn();
		}

		try
		{
			Thread.currentThread().sleep(8 * 60 * 1000);
		}
		catch (InterruptedException e)
		{
		}

		long end = System.nanoTime();

		long duration = end - start;

		int nbProcessed = 0;
		for (SpyActor actor : spies)
			nbProcessed += actor.getReceivedAnswers();

		double secondsElapsed = duration / 1000000000.0d;
		double speed = nbProcessed / secondsElapsed;

		System.out.println("finished " + nbProcessed + " answers in " + duration / 1000000
				+ " ms speed="
				+ speed + " answers/s.");
	}
}
