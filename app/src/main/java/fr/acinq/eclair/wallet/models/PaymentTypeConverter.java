package fr.acinq.eclair.wallet.models;

import org.greenrobot.greendao.converter.PropertyConverter;

public class PaymentTypeConverter implements PropertyConverter<PaymentType, String> {
  @Override
  public PaymentType convertToEntityProperty(String databaseValue) {
    return PaymentType.valueOf(databaseValue);
  }

  @Override
  public String convertToDatabaseValue(PaymentType entityProperty) {
    return entityProperty.name();
  }
}
