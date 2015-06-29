package fr.lteconsulting;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;

public final class MyCoroutine implements Coroutine
{
	@Override
	public void run(Continuation c) throws Exception
	{
		System.out.println("started");
		for (int i = 0; i < 10; i++)
		{
			echo(c, i);
		}
	}

	private void echo(Continuation c, int x)
	{
		System.out.println(x);
		c.suspend();
	}
}