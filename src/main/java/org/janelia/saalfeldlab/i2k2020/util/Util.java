package org.janelia.saalfeldlab.i2k2020.util;

import ij.process.FloatProcessor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Some useful methods that do not fit elsewhere.
 *
 * @author Stephan Saalfeld
 */
public interface Util {

	/**
	 * Copy the contents of an source {@link RandomAccessible} in an
	 * interval defined by and target {@link RandomAccessibleInterval}
	 * into that target {@link RandomAccessibleInterval}.
	 *
	 * @param <T>
	 * @param source
	 * @param target
	 */
	public static <T extends Type<T>> void copy(
			final RandomAccessible<? extends T> source,
			final RandomAccessibleInterval<T> target) {

		Views.flatIterable(Views.interval(Views.pair(source, target), target)).forEach(
				pair -> pair.getB().set(pair.getA()));
	}

	/**
	 * Copy the contents of an source {@link RandomAccessible} in an
	 * interval defined by and target {@link RandomAccessibleInterval}
	 * into that target {@link RandomAccessibleInterval}.
	 *
	 * @param <T>
	 * @param source
	 * @param target
	 */
	public static <T extends RealType<T>, S extends RealType<S>> void copyReal(
			final RandomAccessible<? extends T> source,
			final RandomAccessibleInterval<? extends S> target) {

		Views.flatIterable(Views.interval(Views.pair(source, target), target)).forEach(
				pair -> pair.getB().setReal(pair.getA().getRealDouble()));
	}

	/**
	 * Materialize the first 2D slice of a {@link RandomAccessibleInterval}
	 * of {@link FloatType} into a new ImageJ {@link FloatProcessor}.
	 *
	 * @param source
	 * @return
	 */
	public static FloatProcessor materialize(final RandomAccessibleInterval<FloatType> source) {
		final FloatProcessor target = new FloatProcessor((int) source.dimension(0), (int) source.dimension(1));
		Util.copy(
				Views.zeroMin(source),
				ArrayImgs.floats(
						(float[]) target.getPixels(),
						target.getWidth(),
						target.getHeight()));
		return target;
	}
}
