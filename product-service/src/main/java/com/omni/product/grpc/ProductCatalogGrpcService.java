package com.omni.product.grpc;

import com.omni.product.domain.Product;
import com.omni.product.service.ProductService;
import com.omni.proto.product.GetSkusRequest;
import com.omni.proto.product.GetSkusResponse;
import com.omni.proto.product.ProductCatalogGrpc;
import com.omni.proto.product.SkuInfo;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.HashSet;
import java.util.Set;

/**
 * Internal gRPC endpoint (port 9090, never routed by the gateway) used by the Order Service
 * to take a price/name snapshot of the SKUs being ordered.
 */
@GrpcService
@RequiredArgsConstructor
public class ProductCatalogGrpcService extends ProductCatalogGrpc.ProductCatalogImplBase {

    private final ProductService productService;

    @Override
    public void getSkus(GetSkusRequest request, StreamObserver<GetSkusResponse> responseObserver) {
        Set<String> wanted = new HashSet<>(request.getSkusList());
        GetSkusResponse.Builder response = GetSkusResponse.newBuilder();
        for (Product product : productService.findBySkus(wanted)) {
            String image = product.getImages().isEmpty() ? "" : product.getImages().get(0);
            for (Product.Variant v : product.getVariants()) {
                if (!wanted.contains(v.getSku())) {
                    continue;
                }
                response.addItems(SkuInfo.newBuilder()
                        .setSku(v.getSku())
                        .setProductId(product.getId())
                        .setProductName(product.getName())
                        .setVariantName(v.getName() == null ? "" : v.getName())
                        .setPrice(v.getPrice())
                        .setActive(v.isActive() && product.getStatus() == Product.Status.ACTIVE)
                        .setImageUrl(v.getImage() != null ? v.getImage() : image)
                        .build());
            }
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}
