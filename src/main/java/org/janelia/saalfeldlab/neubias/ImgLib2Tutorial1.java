package org.janelia.saalfeldlab.neubias;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;

public class ImgLib2Tutorial1 {

	public static void main(final String[] s) throws InterruptedException {

		final double cr = 0.2;
		final double ci = 0.6;

		final FunctionRealRandomAccessible<IntType> juliaSet = new FunctionRealRandomAccessible<>(
				2,
				(x, fx) -> {
					int i = 0;
					double v = 0, c = x.getDoublePosition(0), d = x.getDoublePosition(1);
					while (i < 64 && v < 4096) {
						final double e = c * c - d * d;
						d = 2 * c * d;
						c = e + cr;
						d += ci;
						v = Math.sqrt(c * c + d * d);
						++i;
					}
					fx.set(i);
				},
				IntType::new);

		BdvFunctions.show(
				juliaSet,
				Intervals.createMinMax(-1, -1, 1, 1),
				"Julia set",
				BdvOptions.options().is2D()).setDisplayRange(0, 64);
	}

}
