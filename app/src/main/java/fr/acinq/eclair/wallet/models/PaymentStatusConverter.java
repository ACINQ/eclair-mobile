package fr.acinq.eclair.wallet.models;

import org.greenrobot.greendao.converter.PropertyConverter;

public class PaymentStatusConverter implements PropertyConverter<PaymentStatus, String> {
  @Override
  public PaymentStatus convertToEntityProperty(String databaseValue) {
    return PaymentStatus.valueOf(databaseValue);
  }

  @Override
  public String convertToDatabaseValue(PaymentStatus entityProperty) {
    return entityProperty.name();
  }
}
