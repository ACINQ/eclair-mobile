package fr.acinq.eclair.wallet;

import android.content.Context;

import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

import fr.acinq.eclair.wallet.models.DaoMaster;
import fr.acinq.eclair.wallet.models.DaoSession;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDao;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentType;

public class DBHelper {

  public static final boolean IS_DB_ENCRYPTED = true;
  private DaoSession daoSession;

  public DBHelper(Context context) {

    DBMigrationHelper helper = new DBMigrationHelper(context,
      IS_DB_ENCRYPTED ? "eclair-wallet-enc" : "eclair-wallet");
    Database db = IS_DB_ENCRYPTED ? helper.getEncryptedReadableDb("temp_secret") : helper.getWritableDb();
    daoSession = new DaoMaster(db).newSession();
  }

  public DaoSession getDaoSession() {
    return daoSession;
  }


  /**
   * Returns unique Payment with reference and type
   *
   * @param reference Tx id for onchain payments, payment hash for LN payments
   * @param type type of the payment
   * @param direction received, sent, forwarded
   * @return
   */
  public Payment getPayment(String reference, PaymentType type, PaymentDirection direction) {
    QueryBuilder<Payment> qb = daoSession.getPaymentDao().queryBuilder();
    qb.where(PaymentDao.Properties.Reference.eq(reference),
      PaymentDao.Properties.Direction.eq(direction),
      PaymentDao.Properties.Type.eq(type));
    return qb.unique();
  }

  /**
   * Returns unique offchain or onchain Bitcoin Payment stored in DB
   * @param reference payment hash of the LN payment
   * @param type of the payment (onchain, offchain)
   * @return
   * @throws DaoException
   */
  public Payment getPayment(String reference, PaymentType type) throws DaoException {
    QueryBuilder<Payment> qb = daoSession.getPaymentDao().queryBuilder();
    qb.where(PaymentDao.Properties.Reference.eq(reference),
      PaymentDao.Properties.Type.eq(type));
    return qb.unique();
  }

  public void insertOrUpdatePayment(Payment p) {
    daoSession.getPaymentDao().insertOrReplace(p);
  }

  public void updatePayment(Payment p) {
    daoSession.getPaymentDao().update(p);
  }

  public Payment getPayment(Long id) {
    return daoSession.getPaymentDao().load(id);
  }
}
