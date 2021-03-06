package com.qcadoo.mes.masterOrders.hooks;

import com.qcadoo.mes.masterOrders.constants.MasterOrderFields;
import com.qcadoo.mes.masterOrders.constants.MasterOrderState;
import com.qcadoo.mes.masterOrders.constants.MasterOrdersConstants;
import com.qcadoo.mes.masterOrders.constants.OrderFieldsMO;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OrderHooksMO {

    private static final String MASTER_ORDER_POSITIONS_QUERY = "SELECT pos FROM #masterOrders_masterOrderPositionDto pos WHERE masterOrderId = :masterOrderId";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    public void onSave(final DataDefinition orderDD, final Entity order) {
        Entity orderDb = null;
        if (Objects.nonNull(order.getId())) {
            orderDb = orderDD.get(order.getId());
        }
        if (Objects.nonNull(order.getBelongsToField(OrderFieldsMO.MASTER_ORDER))
                && (MasterOrderState.IN_EXECUTION.getStringValue().equals(
                        order.getBelongsToField(OrderFieldsMO.MASTER_ORDER).getStringField(MasterOrderFields.STATE)) || MasterOrderState.NEW
                        .getStringValue().equals(
                                order.getBelongsToField(OrderFieldsMO.MASTER_ORDER).getStringField(MasterOrderFields.STATE)))
                && canChangeToCompltead(order, orderDb)) {
            changeToCompleted(order, orderDb);
        } else if (canChangeMasterOrderStateToInExecution(order, orderDb)) {
            changeToInExecution(order);
        } else if (canChangeMasterOrderStateToNew(order, orderDb)) {
            changeToNew(order, orderDb);
        }
    }

    private void changeToCompleted(Entity order, Entity orderDb) {
        Entity mo = order.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        mo.setField(MasterOrderFields.STATE, MasterOrderState.COMPLETED.getStringValue());
        mo = mo.getDataDefinition().save(mo);
        order.setField(OrderFieldsMO.MASTER_ORDER, mo);

    }

    private boolean canChangeToCompltead(Entity order, Entity orderDb) {
        Entity mo = order.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        List<Entity> positions = dataDefinitionService
                .get(MasterOrdersConstants.PLUGIN_IDENTIFIER, MasterOrdersConstants.MODEL_MASTER_ORDER_POSITION_DTO)
                .find(MASTER_ORDER_POSITIONS_QUERY).setParameter("masterOrderId", mo.getId().intValue()).list().getEntities();

        BigDecimal doneQuantity = BigDecimalUtils.convertNullToZero(order.getDecimalField(OrderFields.DONE_QUANTITY));
        BigDecimal done = BigDecimal.ZERO;
        if(Objects.nonNull(orderDb)){
            BigDecimal doneQuantityDB = orderDb.getDecimalField(OrderFields.DONE_QUANTITY);
            done = BigDecimalUtils.convertNullToZero(doneQuantity).subtract(
                    BigDecimalUtils.convertNullToZero(doneQuantityDB), numberService.getMathContext());
        } else {
            done = doneQuantity;
        }

        for (Entity position : positions) {
            if (position.getIntegerField("productId").equals(order.getBelongsToField(OrderFields.PRODUCT).getId().intValue())) {
                BigDecimal value = position.getDecimalField("producedOrderQuantity").add(done, numberService.getMathContext());
                if (value.compareTo(doneQuantity) == -1) {
                    value = doneQuantity;
                }
                position.setField("producedOrderQuantity", value);
            }
        }

        List<Entity> producedPositions = positions
                .stream()
                .filter(pos -> pos.getDecimalField("producedOrderQuantity").compareTo(pos.getDecimalField("masterOrderQuantity")) == 1
                        || pos.getDecimalField("producedOrderQuantity").compareTo(pos.getDecimalField("masterOrderQuantity")) == 0)
                .collect(Collectors.toList());

        return positions.size() == producedPositions.size();
    }

    private void changeToNew(Entity order, Entity orderDb) {
        Entity moDB = orderDb.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        String masterOrderStatus = moDB.getStringField(MasterOrderFields.STATE);
        if (MasterOrderState.IN_EXECUTION.getStringValue().equals(masterOrderStatus)
                && moDB.getHasManyField(MasterOrderFields.ORDERS).size() == 1) {
            moDB.setField(MasterOrderFields.STATE, MasterOrderState.NEW.getStringValue());
            moDB.getDataDefinition().save(moDB);
        }
    }

    private void changeToInExecution(Entity order) {
        Entity masterOrder = order.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        String masterOrderStatus = masterOrder.getStringField(MasterOrderFields.STATE);
        if (MasterOrderState.NEW.getStringValue().equals(masterOrderStatus)) {
            masterOrder.setField(MasterOrderFields.STATE, MasterOrderState.IN_EXECUTION.getStringValue());
            masterOrder = masterOrder.getDataDefinition().save(masterOrder);
            order.setField(OrderFieldsMO.MASTER_ORDER, masterOrder);
        }
    }

    private boolean canChangeMasterOrderStateToInExecution(final Entity order, final Entity orderDB) {
        Entity mo = order.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        Entity moDB = null;
        if(Objects.nonNull(orderDB)){
            moDB = orderDB.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        }

        if (Objects.isNull(mo) && Objects.isNull(moDB)) {
            return false;
        } else if (Objects.nonNull(mo) && Objects.isNull(moDB)) {
            return true;
        } else if (Objects.isNull(mo) && Objects.nonNull(moDB)) {
            return false;
        } else if (Objects.nonNull(mo) && Objects.nonNull(moDB)) {
            if (mo.getId().equals(moDB.getId())) {
                return false;
            } else {
                return true;
            }
        }

        return true;
    }

    private boolean canChangeMasterOrderStateToNew(final Entity order, final Entity orderDB) {
        Entity mo = order.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        Entity moDB = null;
        if(Objects.nonNull(orderDB)){
            moDB = orderDB.getBelongsToField(OrderFieldsMO.MASTER_ORDER);
        }
        if (Objects.isNull(mo) && Objects.nonNull(moDB)) {
            return true;
        }
        return false;

    }
}
