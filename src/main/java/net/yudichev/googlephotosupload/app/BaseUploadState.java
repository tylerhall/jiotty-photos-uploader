package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@PackagePrivateImmutablesStyle
@JsonSerialize
@JsonDeserialize
interface BaseUploadState {
    @Value.Parameter
    Map<String, ItemState> uploadedMediaItemIdByAbsolutePath();
}
