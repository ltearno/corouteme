package fr.lteconsulting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.SelectAction;
import co.paralleluniverse.strands.channels.Selector;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;

/**
 * Spy:
 * <p/>
 * - boucle de message - un message correspond a un appel RPC - une réponse est renvoyée - => il est possible de wrapper
 * une interface
 * <p/>
 * - un Channel de réception des messages - un getter d'interface nommée (facades)
 */
public class Spy
{
	private final Channel<SpyMessage> msgChannel;

	private final Fiber<Integer> fiber;

	private final List<WaitingSpy> waitingSpies = new ArrayList<>();

	class WaitingSpy
	{
		private Channel<?> responseChannel;

		private final CoroutineRunner runner;

		/** Message to be processed */
		private final SpyMessage message;

		public WaitingSpy(SpyMessage message)
		{
			this.message = message;
			this.runner = new CoroutineRunner(coroutine);
		}

		public boolean step()
		{
			return runner.execute();
		}

		public Coroutine getCoroutine()
		{
			return coroutine;
		}

		public Channel<?> getResponseChannel()
		{
			return responseChannel;
		}

		private Coroutine coroutine = new Coroutine()
		{
			@Override
			public void run(Continuation continuation) throws Exception
			{
				System.out.println("Start of the continuation, processing message " + message);
				// Process the message
				// and provide a "callSpy" method that makes this spy wait for the other spy
			}
		};
	}

	public Spy()
	{
		msgChannel = Channels.newChannel(0);
		fiber = new Fiber<Integer>(this::fiberExecution);
	}

	private Integer fiberExecution()
	{
		while (true)
		{
			pumpMessage();
		}

		// msgChannel.close();

		// return 0;
	}

	@Suspendable
	private void pumpMessage()
	{
		try
		{
			HashMap<Integer, WaitingSpy> spyBySelect = new HashMap<>();

			List<SelectAction<Object>> actions = new ArrayList<>();
			actions.add(Selector.receive(msgChannel));
			for (WaitingSpy spy : waitingSpies)
			{
				if (spy.responseChannel == null)
					continue;

				SelectAction<Object> action = Selector.receive(spy.responseChannel);
				actions.add(action);
				spyBySelect.put(System.identityHashCode(action), spy);
			}

			SelectAction<Object> selected = Selector.select(actions);

			if (selected == null)
			{
				System.out.println("NOTHING SELECTED !!!");
				return;
			}

			if (selected == actions.get(0))
			{
				System.out.println("RECEIVED A MESSAGE TO PROCESS " + selected.message());

				// Coroutine creation
				WaitingSpy spy = new WaitingSpy((SpyMessage)selected.message());

				spy.step();
			}
			else
			{
				System.out.println("FINISHED AN IO OPERATION, CONTIUING A SPY");

				WaitingSpy waitingSpy = spyBySelect.get(selected);
				if (waitingSpy == null)
				{
					System.out.println("WEIRD SELECTED OPERATION BUT NON WAITING SPY ON IT !");
					return;
				}

				waitingSpy.step();
			}
		}
		catch (SuspendExecution suspendExecution)
		{
			suspendExecution.printStackTrace();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	protected Object callSpy(Spy spy, String methodName, Object[] parameters)
	{
		Channel responseChannel = Channels.newChannel(0);
		try
		{
			spy.msgChannel.send(new SpyMessage(methodName, parameters, responseChannel));

			// register responseChannel
			// loop message pump
			// Object result = loopMessagePumpForResponseMsgChannel(responseChannel);
			// return result

			return null;
		}
		catch (SuspendExecution suspendExecution)
		{
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public void send(String methodName, Object[] parameters)
	{
		try
		{
			msgChannel.send(new SpyMessage(methodName, parameters, Channels.newChannel(0)));
		}
		catch (SuspendExecution | InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	private void processMessage(SpyMessage msg)
	{
		Object result = null;
		try
		{
			msg.getResponseChannel().send(result);
		}
		catch (SuspendExecution | InterruptedException e)
		{
		}
	}

	public void start()
	{
		fiber.start();
	}
}

class SpyMessage
{
	private final String methodName;

	private final Object[] parameters;

	private final Channel<Object> responseChannel;

	SpyMessage(String methodName, Object[] parameters, Channel<Object> responseChannel)
	{
		this.methodName = methodName;
		this.parameters = parameters;
		this.responseChannel = responseChannel;
	}

	public String getMethodName()
	{
		return methodName;
	}

	public Object[] getParameters()
	{
		return parameters;
	}

	public Channel<Object> getResponseChannel()
	{
		return responseChannel;
	}
}
