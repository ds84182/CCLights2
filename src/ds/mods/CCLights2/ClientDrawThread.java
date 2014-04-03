package ds.mods.CCLights2;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;


public class ClientDrawThread extends Thread {
	public WeakHashMap<GPU, Deque<DrawCMD>> draws = new WeakHashMap<GPU, Deque<DrawCMD>>();

	@Override
	public void run() {
		while (true)
		{
			synchronized (draws)
			{
				Iterator<Entry<GPU,Deque<DrawCMD>>> iter = draws.entrySet().iterator();
				synchronized (iter)
				{
					while (iter.hasNext())
					{
						Entry<GPU,Deque<DrawCMD>> e = iter.next();
						GPU gpu = e.getKey();
						Deque<DrawCMD> stack = e.getValue();
						synchronized (gpu)
						{
							synchronized (stack)
							{
								if (gpu.currentMonitor == null) continue;
								synchronized (gpu.currentMonitor)
								{
									synchronized (gpu.currentMonitor.tex)
									{
										gpu.currentMonitor.tex.renderLock = true;
										while (!stack.isEmpty())
										{
											try {
												DrawCMD d = stack.poll();
												if (d == null) continue;
												gpu.processCommand(d);
											} catch (Exception e1) {
												CCLights2.debug("Unable to process cmd in clientdrawthread");
											}
										}
										gpu.currentMonitor.tex.texUpdate();
										gpu.currentMonitor.tex.renderLock = false;
										gpu.currentMonitor.tex.notifyAll();
									}
								}
							}
						}
					}
				}
			}
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				CCLights2.debug("ClientDrawThread is unable to sleep.");
			}
		}
	}

}
