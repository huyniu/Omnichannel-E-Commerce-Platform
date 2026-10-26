package com.omni.order.client;

import com.omni.common.exception.BusinessException;
import com.omni.proto.product.GetSkusRequest;
import com.omni.proto.product.ProductCatalogGrpc;
import com.omni.proto.product.SkuInfo;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Synchronous gRPC (HTTP/2, protobuf) call to the Product Catalog Service. A short deadline keeps a
 * slow catalog from tying up order threads.
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    @GrpcClient("product-service")
    private ProductCatalogGrpc.ProductCatalogBlockingStub stub;

    @Value("${order.product-deadline-ms:2000}")
    private long deadlineMs;

    public Map<String, SkuInfo> getSkus(Collection<String> skus) {
        try {
            return stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .getSkus(GetSkusRequest.newBuilder().addAllSkus(skus).build())
                    .getItemsList().stream()
                    .collect(Collectors.toMap(SkuInfo::getSku, Function.identity()));
        } catch (StatusRuntimeException e) {
            log.error("Product service call failed: {}", e.getStatus());
            throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE",
                    "Product catalog is temporarily unavailable, please retry", 503);
        }
    }
}
