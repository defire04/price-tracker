package com.price.tracker.interfaces.telegram.wizard;

import com.price.tracker.domain.enums.Site;
import com.price.tracker.domain.enums.TrackedEventType;
import com.price.tracker.domain.model.ProductSnapshot;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;

@Getter
@Setter
public class TrackWizardState {

    private final Instant startedAt = Instant.now();
    private final EnumSet<TrackedEventType> selectedEvents = EnumSet.noneOf(TrackedEventType.class);
    private TrackWizardStep step = TrackWizardStep.AWAIT_URL;
    private int menuMessageId;
    private String url;
    private Site site;
    private ProductSnapshot product;
    private String sizeLabel;

    public void initProduct(String url, Site site, ProductSnapshot product) {
        this.url = url;
        this.site = site;
        this.product = product;
        this.step = TrackWizardStep.CHOOSE_SIZE;
    }

    public void toggleEvent(TrackedEventType eventType) {
        if (!selectedEvents.remove(eventType)) {
            selectedEvents.add(eventType);
        }
    }
}
