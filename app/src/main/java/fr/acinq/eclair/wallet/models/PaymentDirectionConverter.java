package fr.acinq.eclair.wallet.models;

import org.greenrobot.greendao.converter.PropertyConverter;

public class PaymentDirectionConverter implements PropertyConverter<PaymentDirection, String> {
  @Override
  public PaymentDirection convertToEntityProperty(String databaseValue) {
    return PaymentDirection.valueOf(databaseValue);
  }

  @Override
  public String convertToDatabaseValue(PaymentDirection entityProperty) {
    return entityProperty.name();
  }
}
