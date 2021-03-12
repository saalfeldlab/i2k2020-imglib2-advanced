package org.janelia.saalfeldlab.neubias.util;


public class Timer {

	private long t = 0;
	private double a = 1.0 / 1000000;

	public void start() {

		t = System.nanoTime();
	}

	public double stop() {

		return (System.nanoTime() - t) * a;
	}
}
