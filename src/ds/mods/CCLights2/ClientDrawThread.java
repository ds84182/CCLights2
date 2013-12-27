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
				while (iter.hasNext())
				{
					Entry<GPU,Deque<DrawCMD>> e = iter.next();
					synchronized (e.getValue())
					{
						if (e.getKey().currentMonitor == null) continue;
						synchronized (e.getKey().currentMonitor.tex)
						{
							e.getKey().currentMonitor.tex.renderLock = true;
							Deque<DrawCMD> stack = e.getValue();
							while (!stack.isEmpty())
							{
								try {
									DrawCMD d = stack.poll();
									if (d == null) continue;
									e.getKey().processCommand(d);
								} catch (Exception e1) {
									e1.printStackTrace();
								}
							}
							e.getKey().currentMonitor.tex.renderLock = false;
							e.getKey().currentMonitor.tex.notifyAll();
						}
					}
				}
			}
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
