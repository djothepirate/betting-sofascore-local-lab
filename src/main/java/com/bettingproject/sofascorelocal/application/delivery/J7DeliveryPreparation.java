package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;

import java.util.Objects;
import java.util.Optional;

/**
 * Trusted server-side preparation result.
 *
 * <p>The owner-go reference is intentionally kept outside {@link J7DeliveryView}, which is added
 * to the web model. This object must never itself be exposed to a template.</p>
 */
public final class J7DeliveryPreparation {

    private final J7DeliveryView view;
    private final Optional<J7ProviderDerivedOwnerGo.Reference> providerOwnerGoReference;

    public J7DeliveryPreparation(
            J7DeliveryView view,
            Optional<J7ProviderDerivedOwnerGo.Reference> providerOwnerGoReference) {
        this.view = Objects.requireNonNull(view, "view");
        this.providerOwnerGoReference = Objects.requireNonNull(
                providerOwnerGoReference, "providerOwnerGoReference");
        if (view.payloadClass() != J7DeliveryPayloadClass.PROVIDER_DERIVED
                && providerOwnerGoReference.isPresent()) {
            throw new IllegalArgumentException(
                    "only a provider-derived preparation may carry an owner-go reference");
        }
        if (view.payloadClass() == J7DeliveryPayloadClass.PROVIDER_DERIVED
                && view.preparationAllowed()
                && providerOwnerGoReference.isEmpty()) {
            throw new IllegalArgumentException(
                    "an eligible provider-derived preparation requires an owner-go reference");
        }
    }

    public J7DeliveryView view() {
        return view;
    }

    public Optional<J7ProviderDerivedOwnerGo.Reference> providerOwnerGoReference() {
        return providerOwnerGoReference;
    }

    @Override
    public String toString() {
        return "J7DeliveryPreparation[preparationAllowed="
                + view.preparationAllowed() + "]";
    }
}
