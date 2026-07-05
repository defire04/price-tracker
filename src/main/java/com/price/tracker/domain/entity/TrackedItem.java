package com.price.tracker.domain.entity;

import com.price.tracker.domain.enums.Site;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tracked_items", indexes = {
        @Index(name = "idx_tracked_items_user", columnList = "user_id")
})
@Getter
@Setter
public class TrackedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "site", nullable = false, length = 32)
    private Site site;

    // NULL means a one-size product
    @Column(name = "size_label", length = 64)
    private String sizeLabel;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    // User-defined name; falls back to the product title
    @Column(name = "display_name", length = 128)
    private String displayName;

    // Price at the moment tracking was created; fallback base for DISCOUNT_PERCENT
    @Column(name = "original_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_threshold", precision = 5, scale = 2)
    private BigDecimal discountThreshold;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<TrackedItemEvent> events = new ArrayList<>();

    public String displayTitle() {
        return displayName != null ? displayName : title;
    }
}
