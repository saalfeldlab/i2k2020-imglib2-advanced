package org.janelia.saalfeldlab.i2k2020;

import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;

public interface Functions {

	public static <T extends NumericType<T>> RandomAccessible<T> add(
			final RandomAccessible<T> sourceA,
			final RandomAccessible<T> sourceB) {

		return Converters.convert(
				sourceA,
				sourceB,
				(a, b, c) -> {
					c.set(a);
					c.add(b);
				},
				sourceA.randomAccess().get().createVariable());
	}

	public static <T extends NumericType<T>> RandomAccessible<T> sub(
			final RandomAccessible<T> sourceA,
			final RandomAccessible<T> sourceB) {

		return Converters.convert(
				sourceA,
				sourceB,
				(a, b, c) -> {
					c.set(a);
					c.sub(b);
				},
				sourceA.randomAccess().get().createVariable());
	}

	public static <T extends NumericType<T>> RandomAccessible<T> mul(
			final RandomAccessible<T> sourceA,
			final RandomAccessible<T> sourceB) {

		return Converters.convert(
				sourceA,
				sourceB,
				(a, b, c) -> {
					c.set(a);
					c.mul(b);
				},
				sourceA.randomAccess().get().createVariable());
	}

	public static <T extends NumericType<T>> RandomAccessible<T> div(
			final RandomAccessible<T> sourceA,
			final RandomAccessible<T> sourceB) {

		return Converters.convert(
				sourceA,
				sourceB,
				(a, b, c) -> {
					c.set(a);
					c.div(b);
				},
				sourceA.randomAccess().get().createVariable());
	}

	public static <T extends NumericType<T>> RandomAccessible<T> centerGradient(
			final RandomAccessible<T> source,
			final int d) {

		final long[] offset = new long[source.numDimensions()];
		offset[d] = -1;
		final MixedTransformView<T> sourceA = Views.translateInverse(source, offset);
		final MixedTransformView<T> sourceB = Views.translate(source, offset);

		return Converters.convert(
				sourceA,
				sourceB,
				(a, b, c) -> {
					c.set(b);
					c.sub(a);
					c.mul(0.5);
				},
				sourceA.randomAccess().get().createVariable());
	}

	public static <T extends NumericType<T>> RandomAccessibleInterval<T> centerGradientRAI(
			final RandomAccessibleInterval<T> source,
			final int d) {

		return Views.interval(
				centerGradient(Views.extendMirrorSingle(source), d),
				source);
	}
}
