package com.eventstore.dbclient;

import com.eventstore.dbclient.proto.shared.Shared;
import com.eventstore.dbclient.proto.streams.StreamsOuterClass;
import com.google.protobuf.ByteString;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class GrpcUtils {
    static public <ReqT, RespT, TargetT> ClientResponseObserver<ReqT, RespT> convertSingleResponse(
            CompletableFuture<TargetT> dest, Function<RespT, TargetT> converter) {
        return new ClientResponseObserver<ReqT, RespT>() {
            @Override
            public void beforeStart(ClientCallStreamObserver<ReqT> requestStream) {
            }

            @Override
            public void onNext(RespT value) {
                try {
                    TargetT converted = converter.apply(value);
                    dest.complete(converted);
                } catch (Throwable e) {
                    dest.completeExceptionally(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException e = (StatusRuntimeException) t;
                    String leaderHost = e.getTrailers().get(Metadata.Key.of("leader-endpoint-host", Metadata.ASCII_STRING_MARSHALLER));
                    String leaderPort = e.getTrailers().get(Metadata.Key.of("leader-endpoint-port", Metadata.ASCII_STRING_MARSHALLER));

                    if (leaderHost != null && leaderPort != null) {
                        NotLeaderException reason = new NotLeaderException(leaderHost, Integer.valueOf(leaderPort));
                        dest.completeExceptionally(reason);
                        return;
                    }
                }

                dest.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    static public StreamsOuterClass.ReadReq.Options.StreamOptions toStreamOptions(String streamName, StreamRevision revision) {
        StreamsOuterClass.ReadReq.Options.StreamOptions.Builder builder = StreamsOuterClass.ReadReq.Options.StreamOptions.newBuilder()
                .setStreamIdentifier(Shared.StreamIdentifier.newBuilder()
                        .setStreamName(ByteString.copyFromUtf8(streamName))
                        .build());

        if (revision == StreamRevision.END) {
            return builder.setEnd(Shared.Empty.getDefaultInstance())
                    .build();
        }

        if (revision == StreamRevision.START) {
            return builder.setStart(Shared.Empty.getDefaultInstance())
                    .build();
        }

        return builder.setRevision(revision.getValueUnsigned())
                .build();
    }
}
