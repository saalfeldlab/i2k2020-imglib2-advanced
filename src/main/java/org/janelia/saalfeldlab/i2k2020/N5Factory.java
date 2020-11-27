package org.janelia.saalfeldlab.i2k2020;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;

/**
 * Factory methods for various N5 readers and writers.  The factory methods
 * do not expose all parameters of each reader or writer to keep things
 * simple in this tutorial.  In particular, custom JSON adapters for not so
 * simple types are not considered, albeit incredibly useful.  Please inspect
 * the constructors of the readers and writers for further parameters.
 *
 * @author Stephan Saalfeld
 */
public interface N5Factory {

	/**
	 * Helper method.
	 *
	 * @param url
	 * @return
	 */
	public static AmazonS3 createS3(final String url) {

		AmazonS3 s3;
		AWSCredentials credentials = null;
		try {
			credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
		} catch(final Exception e) {
			System.out.println( "Could not load AWS credentials, falling back to anonymous." );
		}
		final AWSStaticCredentialsProvider credentialsProvider =
				new AWSStaticCredentialsProvider(credentials == null ? new AnonymousAWSCredentials() : credentials);

		final AmazonS3URI uri = new AmazonS3URI(url);
		final Optional<String> region = Optional.ofNullable(uri.getRegion());

		if(region.isPresent()) {
			s3 = AmazonS3ClientBuilder.standard()
					.withCredentials(credentialsProvider)
					.withRegion(region.map(Regions::fromName).orElse(Regions.US_EAST_1))
					.build();
		} else {
			s3 = AmazonS3ClientBuilder.standard()
					.withCredentials(credentialsProvider)
					.withRegion(Regions.US_EAST_1)
					.build();
		}

		return s3;
	}

	public static N5Reader openN5FSReader(final String path) throws IOException {

		return new N5FSReader(path);
	}

	public static N5Reader openZarrReader(final String path) throws IOException {

		return new N5ZarrReader(path);
	}

	public static N5Reader openN5HDF5Reader(final String path, final int... defaultBlockSize) throws IOException {

		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(path);
		return new N5HDF5Reader(hdf5Reader, defaultBlockSize);
	}

	public static N5Reader openN5GoogleCloudReader(final String url) throws IOException {

		final GoogleCloudStorageClient storageClient = new GoogleCloudStorageClient();
		final Storage storage = storageClient.create();
		final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI(url);
		final String bucketName = googleCloudUri.getBucket();
		return new N5GoogleCloudStorageReader(storage, bucketName);
	}

	public static N5Reader openN5AWSS3Reader(final String url) throws IOException {

		return new N5AmazonS3Reader(createS3(url), new AmazonS3URI(url));
	}

	public static N5Writer openN5FSWriter(final String path) throws IOException {

		return new N5FSWriter(path);
	}

	public static N5Writer openZarrWriter(final String path) throws IOException {

		return new N5ZarrWriter(path);
	}

	public static N5Writer openN5HDF5Writer(final String path, final int... defaultBlockSize) throws IOException {

		final IHDF5Writer hdf5Writer = HDF5Factory.open(path);
		return new N5HDF5Writer(hdf5Writer, defaultBlockSize);
	}

	public static N5Writer openN5GoogleCloudWriter(final String url, final String projectId) throws IOException {

		final GoogleCloudStorageClient storageClient;
		if (projectId == null) {
			final ResourceManager resourceManager = new GoogleCloudResourceManagerClient().create();
			final Iterator<Project> projectsIterator = resourceManager.list().iterateAll().iterator();
			if (!projectsIterator.hasNext())
				return null;
			storageClient = new GoogleCloudStorageClient(projectsIterator.next().getProjectId());
		} else
			storageClient = new GoogleCloudStorageClient(projectId);

		final Storage storage = storageClient.create();
		final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI(url);
		final String bucketName = googleCloudUri.getBucket();
		return new N5GoogleCloudStorageWriter(storage, bucketName);
	}

	public static N5Writer openN5AWSS3Writer(final String url) throws IOException {

		return new N5AmazonS3Writer(createS3(url), new AmazonS3URI(url));
	}
}
