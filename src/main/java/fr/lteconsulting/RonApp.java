package fr.lteconsulting;

import java.util.concurrent.ExecutionException;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberExecutorScheduler;
import co.paralleluniverse.fibers.FiberScheduler;

public class RonApp
{
	public static void main(String[] args)
	{
		try
		{
			new Fiber<Void>(() -> {
				FiberScheduler myScheduler = new FiberExecutorScheduler("cont", r -> r.run());

				Fiber<Void> fiber = new Fiber<Void>(myScheduler, () -> {
					System.out.println("Starting");

					System.out.println("fiber thread before parking id " + Thread.currentThread().getId());

					Fiber.park();

					System.out.println("fiber after unparking thread id " + Thread.currentThread().getId());

					System.out.println("Finished");
				});

				System.out.println("main thread id " + Thread.currentThread().getId());

				System.out.println("starting");
				fiber.start();
				System.out.println("started");
				while (!fiber.isDone())
				{
					System.out.println("unparking");
					fiber.unpark();
					System.out.println("unparked");
				}

			}).start().join();
		}
		catch (ExecutionException e)
		{
		}
		catch (InterruptedException e)
		{
		}

		System.out.println("Terminated");
	}
}
