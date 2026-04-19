package com.meridianmart.service;

import com.meridianmart.dto.PagedResponse;
import com.meridianmart.dto.ProductDto;
import com.meridianmart.model.BehaviorEvent;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.BehaviorEventRepository;
import com.meridianmart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BehaviorEventRepository behaviorEventRepository;

    @Transactional(readOnly = true)
    public PagedResponse<ProductDto> getCatalog(String storeId, int page, int size) {
        Page<Product> productPage = productRepository.findByStoreId(storeId, PageRequest.of(page, size));
        List<ProductDto> items = productPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PagedResponse.<ProductDto>builder()
                .items(items)
                .page(page)
                .size(size)
                .total(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id, String storeId) {
        Product product = productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id));
        return toDto(product);
    }

    @Transactional(readOnly = true)
    public Product getProductEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Product getProductEntityByIdAndStore(Long id, String storeId) {
        return productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id));
    }

    @Transactional
    public Product decrementStock(Long productId, int quantity) {
        Product product = getProductEntityById(productId);
        if (product.getStockQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient stock for product: " + product.getName());
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        return productRepository.save(product);
    }

    @Transactional
    public Product incrementStock(Long productId, int quantity) {
        Product product = getProductEntityById(productId);
        product.setStockQuantity(product.getStockQuantity() + quantity);
        return productRepository.save(product);
    }

    @Transactional
    public void recordViewEvent(User user, Long productId) {
        productRepository.findByIdAndStoreId(productId, user.getStoreId()).ifPresent(product ->
                behaviorEventRepository.save(BehaviorEvent.builder()
                        .user(user).product(product).eventType(BehaviorEvent.EventType.VIEW).build())
        );
    }

    public ProductDto toDto(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .newArrival(product.isNewArrival())
                .stockWarning(product.getStockQuantity() < 2)
                .createdAt(product.getCreatedAt() != null ? product.getCreatedAt().toString() : null)
                .build();
    }
}
