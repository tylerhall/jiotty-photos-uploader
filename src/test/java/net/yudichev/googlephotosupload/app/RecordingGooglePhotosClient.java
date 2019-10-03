package net.yudichev.googlephotosupload.app;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.grpc.Status;
import net.jiotty.connector.google.photos.GoogleMediaItem;
import net.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

final class RecordingGooglePhotosClient implements GooglePhotosClient {
    private static final Logger logger = LoggerFactory.getLogger(RecordingGooglePhotosClient.class);

    private final Map<String, UploadedGoogleMediaItem> itemsById = new ConcurrentHashMap<>();
    private final Map<String, CreatedGooglePhotosAlbum> albumsById = new ConcurrentHashMap<>();
    private final Map<Object, Integer> resourceExhaustionCountByKey = new ConcurrentHashMap<>();
    private boolean resourceExhaustedExceptions;

    @Override
    public CompletableFuture<GoogleMediaItem> uploadMediaItem(Optional<String> albumId, Path path, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            checkArgument(path.toString().endsWith(".jpg"), "Failed: There was an error while trying to create this media item.");
            simulateResourceExhaustion(ImmutableSet.of("uploadMediaItem", path));
            UploadedGoogleMediaItem item = new UploadedGoogleMediaItem(path, albumId);
            itemsById.put(item.getId(), item);
            return item;
        }, executor);
    }

    @Override
    public CompletableFuture<GoogleMediaItem> uploadMediaItem(Path file, Executor executor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<GooglePhotosAlbum> createAlbum(String name, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            simulateResourceExhaustion(ImmutableSet.of("createAlbum", name));
            CreatedGooglePhotosAlbum album = new CreatedGooglePhotosAlbum(name);
            albumsById.put(album.getId(), album);
            return album;
        }, executor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<Collection<GooglePhotosAlbum>> listAlbums(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            simulateResourceExhaustion(ImmutableSet.of("listAlbums"));
            return (Collection<GooglePhotosAlbum>) (Object) albumsById.values();
        }, executor);
    }

    @Override
    public CompletableFuture<GooglePhotosAlbum> getAlbum(String albumId, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            simulateResourceExhaustion(ImmutableSet.of("getAlbum", albumId));
            GooglePhotosAlbum album = albumsById.get(albumId);
            checkArgument(album != null, "unknown album id: %s", albumId);
            return album;
        }, executor);
    }

    void enableResourceExhaustedExceptions() {
        resourceExhaustedExceptions = true;
    }

    Collection<UploadedGoogleMediaItem> getAllItems() {
        return itemsById.values();
    }

    private void simulateResourceExhaustion(Object key) {
        if (!resourceExhaustedExceptions) {
            return;
        }
        int currentCount = resourceExhaustionCountByKey.compute(key, (ignored, count) -> {
            if (count == null) {
                count = 2;
            }
            return --count;
        });
        if (currentCount > 0) {
            logger.debug("Simulating resource exhaustion for {}, retries left: {}", key, currentCount);
            throw new ResourceExhaustedException(
                    new RuntimeException("exhausted"),
                    GrpcStatusCode.of(Status.Code.RESOURCE_EXHAUSTED),
                    true);
        } else {
            logger.debug("Resource exhaustion count depleted for {} ({}), not simulating exhaustion", key, currentCount);
        }
    }

    public static class UploadedGoogleMediaItem implements GoogleMediaItem {
        private final String id;
        private final Optional<String> albumId;
        private final Path file;

        UploadedGoogleMediaItem(Path file, Optional<String> albumId) {
            this.file = checkNotNull(file);
            id = file.toAbsolutePath().toString();
            this.albumId = checkNotNull(albumId);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("file", file)
                    .add("albumId", albumId)
                    .toString();
        }

        Path getFile() {
            return file;
        }

        Optional<String> getAlbumId() {
            return albumId;
        }
    }

    public static class CreatedGooglePhotosAlbum implements GooglePhotosAlbum {
        private final String name;

        CreatedGooglePhotosAlbum(String name) {
            this.name = checkNotNull(name);
        }

        @Override
        public CompletableFuture<Void> addPhotosByIds(List<String> mediaItemsIds) {
            throw new UnsupportedOperationException("addPhotosByIds");
        }

        @Override
        public String getTitle() {
            return name;
        }

        @Override
        public String getId() {
            return name;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .toString();
        }
    }
}
