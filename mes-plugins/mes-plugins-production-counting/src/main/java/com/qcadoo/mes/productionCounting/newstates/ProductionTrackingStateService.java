package com.qcadoo.mes.productionCounting.newstates;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.mes.basic.LogService;
import com.qcadoo.mes.newstates.BasicStateService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.productionCounting.ProductionTrackingService;
import com.qcadoo.mes.productionCounting.constants.ProductionCountingConstants;
import com.qcadoo.mes.productionCounting.constants.ProductionTrackingFields;
import com.qcadoo.mes.productionCounting.states.constants.ProductionTrackingStateChangeDescriber;
import com.qcadoo.mes.productionCounting.states.constants.ProductionTrackingStateStringValues;
import com.qcadoo.mes.productionCounting.states.listener.ProductionTrackingListenerService;
import com.qcadoo.mes.states.StateChangeEntityDescriber;
import com.qcadoo.model.api.Entity;
import com.qcadoo.security.api.UserService;
import com.qcadoo.security.constants.UserFields;

@Service
@Order(1)
public class ProductionTrackingStateService extends BasicStateService implements ProductionTrackingStateServiceMarker {

    @Autowired
    private ProductionTrackingStateChangeDescriber productionTrackingStateChangeDescriber;

    @Autowired
    private ProductionTrackingListenerService productionTrackingListenerService;

    @Autowired
    private ProductionTrackingService productionTrackingService;

    @Autowired
    private LogService logService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private UserService userService;

    @Override
    public StateChangeEntityDescriber getChangeEntityDescriber() {
        return productionTrackingStateChangeDescriber;
    }

    @Override
    public Entity onValidate(Entity entity, String sourceState, String targetState, Entity stateChangeEntity,
            StateChangeEntityDescriber describer) {
        switch (targetState) {
            case ProductionTrackingStateStringValues.ACCEPTED:
                productionTrackingListenerService.validationOnAccept(entity);
                break;
        }

        return entity;
    }

    @Override
    public Entity onBeforeSave(Entity entity, String sourceState, String targetState, Entity stateChangeEntity,
            StateChangeEntityDescriber describer) {
        if (ProductionTrackingStateStringValues.DRAFT.equals(sourceState)) {
            productionTrackingListenerService.onLeavingDraft(entity);
        }

        return entity;
    }

    @Override
    public Entity onAfterSave(Entity entity, String sourceState, String targetState, Entity stateChangeEntity,
            StateChangeEntityDescriber describer) {
        switch (targetState) {
            case ProductionTrackingStateStringValues.ACCEPTED:
                productionTrackingListenerService.onAccept(entity);
                break;

            case ProductionTrackingStateStringValues.DECLINED:
                productionTrackingService.unCorrect(entity);

                if (ProductionTrackingStateStringValues.ACCEPTED.equals(sourceState)) {
                    productionTrackingListenerService.onChangeFromAcceptedToDeclined(entity);
                }
                break;

            case ProductionTrackingStateStringValues.CORRECTED:
                productionTrackingListenerService.onCorrected(entity);
                break;
        }

        if (entity.isValid()) {
            logActivities(entity, targetState);
        }
        return entity;
    }

    private void logActivities(final Entity productionTracking, final String state) {
        Entity user = userService.find(productionTracking.getStringField("createUser"));
        String worker = StringUtils.EMPTY;
        if (user != null) {
            worker = user.getStringField(UserFields.FIRST_NAME) + " " + user.getStringField(UserFields.LAST_NAME);
        }
        String number = productionTracking.getStringField(ProductionTrackingFields.NUMBER);
        String orderNumber = productionTracking.getBelongsToField(ProductionTrackingFields.ORDER).getStringField(
                OrderFields.NUMBER);
        DateTime createDate = new DateTime(productionTracking.getDateField("createDate"));
        logService.add(LogService.Builder.activity(
                "productionTracking",
                translationService.translate("productionCounting.productionTracking.activity." + state + ".action",
                        LocaleContextHolder.getLocale())).withMessage(
                translationService.translate("productionCounting.productionTracking.activity." + state + ".message",
                        LocaleContextHolder.getLocale(), worker, generateDetailsUrl(number, productionTracking.getId()),
                        orderNumber, createDate.toString("HH:mm:ss dd/MM/yyyy"))));
    }

    private String generateDetailsUrl(String number, Long id) {
        return "<a href=\"" + ProductionCountingConstants.productionTrackingDetailsUrl(id) + "\" target=\"_blank\">" + number
                + "</a>";
    }

}
