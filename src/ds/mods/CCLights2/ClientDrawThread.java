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
			synchronized (this)
			{
				Iterator<Entry<GPU,Deque<DrawCMD>>> iter = draws.entrySet().iterator();
				while (iter.hasNext())
				{
					Entry<GPU,Deque<DrawCMD>> e = iter.next();
					GPU gpu = e.getKey();
					Deque<DrawCMD> stack = e.getValue();
					if (gpu.currentMonitor == null) continue;
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
					try {
						//gpu.currentMonitor.tex.notifyAll();
					} catch(Exception eee)
					{
						eee.printStackTrace();
					}
				}
			}
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				CCLights2.debug("Imsomia is a bitch");
			}
		}
	}
}
