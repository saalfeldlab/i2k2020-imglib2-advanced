package org.janelia.saalfeldlab.i2k2020;

import static net.imglib2.img.basictypeaccess.AccessFlags.VOLATILE;
import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.DOUBLE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.type.PrimitiveType.INT;
import static net.imglib2.type.PrimitiveType.LONG;
import static net.imglib2.type.PrimitiveType.SHORT;

import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * A simple method to cache an arbitrary a {@link RandomAccessibleInterval}
 * of the typical {@link NativeType} implementations in a memory cell image
 * with volatile cells.
 *
 * @author Stephan Saalfeld
 */
public interface Caches {

	/**
	 * A simple {@link CellLoader} implementation that fills a pre-allocated
	 * cell with data from a {@link RandomAccessible} source at the same
	 * coordinates.
	 *
	 * @param <T>
	 */
	public static class RandomAccessibleLoader<T extends NativeType<T>> implements CellLoader<T> {

		private final RandomAccessible<T> source;

		public RandomAccessibleLoader(final RandomAccessible<T> source) {

			super();
			this.source = source;
		}

		@Override
		public void load(final SingleCellArrayImg<T, ?> cell) {

			for (Cursor<T> s = Views.flatIterable(Views.interval(source, cell)).cursor(), t = cell.cursor(); s.hasNext();)
				t.next().set(s.next());
		}
	}

	/**
	 * Cache a {@link RandomAccessibleInterval} of the typical
	 * {@link NativeType} implementations in a memory cell image with volatile
	 * cells.  The result can be used with non-volatile types for processing
	 * but it can also be wrapped into volatile types for visualization, see
	 * {@link VolatileViews#wrapAsVolatile(RandomAccessible)}.
	 *
	 * This is a very naive method to implement this kind of cache, but it
	 * serves the purpose for this tutorial.  The imglib2-cache library offers
	 * more control over caches, and you should go and test it out.
	 *
	 * @param <T>
	 * @param source
	 * @param blockSize
	 * @return
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static <T extends NativeType<T>> RandomAccessibleInterval<T> cache(
			final RandomAccessibleInterval<T> source,
			final int... blockSize) {

		final long[] dimensions = Intervals.dimensionsAsLongArray(source);
		final CellGrid grid = new CellGrid(dimensions, blockSize);

		final RandomAccessibleLoader<T> loader = new RandomAccessibleLoader<T>(Views.zeroMin(source));

		final T type = Util.getTypeFromInterval(source);

		final CachedCellImg<T, ?> img;
		final Cache<Long, Cell<?>> cache =
				new SoftRefLoaderCache().withLoader(LoadedCellCacheLoader.get(grid, loader, type, AccessFlags.setOf(VOLATILE)));

		if (GenericByteType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(BYTE, AccessFlags.setOf(VOLATILE)));
		} else if (GenericShortType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(SHORT, AccessFlags.setOf(VOLATILE)));
		} else if (GenericIntType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(INT, AccessFlags.setOf(VOLATILE)));
		} else if (GenericLongType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(LONG, AccessFlags.setOf(VOLATILE)));
		} else if (FloatType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(FLOAT, AccessFlags.setOf(VOLATILE)));
		} else if (DoubleType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(DOUBLE, AccessFlags.setOf(VOLATILE)));
		} else {
			img = null;
		}

		return img;
	}
}
