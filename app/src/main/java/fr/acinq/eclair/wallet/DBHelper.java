package fr.acinq.eclair.wallet;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

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

  private final static String TAG = "DBHelper";
  private DaoSession daoSession;

  public DBHelper(Context context) {

    DBMigrationHelper helper = new DBMigrationHelper(context, "eclair-wallet");
    Database db = helper.getWritableDb();
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
   *
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

  private final static String rawQueryOnchainReceived = new StringBuilder("SELECT SUM(").append(PaymentDao.Properties.AmountPaidMsat.columnName)
    .append(") FROM ").append(PaymentDao.TABLENAME)
    .append(" WHERE ").append(PaymentDao.Properties.Type.columnName).append(" = '").append(PaymentType.BTC_ONCHAIN).append("'")
    .append(" AND ").append(PaymentDao.Properties.Direction.columnName).append(" = '").append(PaymentDirection.RECEIVED).append("'")
    .toString();

  private final static String rawQueryOnchainSent = new StringBuilder("SELECT SUM(").append(PaymentDao.Properties.AmountPaidMsat.columnName)
    .append(") FROM ").append(PaymentDao.TABLENAME)
    .append(" WHERE ").append(PaymentDao.Properties.Type.columnName).append(" = '").append(PaymentType.BTC_ONCHAIN).append("'")
    .append(" AND ").append(PaymentDao.Properties.Direction.columnName).append(" = '").append(PaymentDirection.SENT).append("'")
    .toString();

  /**
   * Returns the current onchain balance by aggregating the on-chain payments known in the database.
   * Used to initialize the onchain wallet balance at the start of the app.
   *
   * @return balance in milli-satoshis.
   */
  public long getOnchainBalanceMsat() {
    final Cursor cursorReceived = daoSession.getDatabase().rawQuery(rawQueryOnchainReceived, new String []{});
    final Cursor cursorSent = daoSession.getDatabase().rawQuery(rawQueryOnchainSent, new String []{});
    long receivedMsat = 0;
    long sentMsat = 0;
    if (cursorReceived.moveToFirst()) {
      receivedMsat = cursorReceived.getLong(0);
    }
    if (cursorSent.moveToFirst()) {
      sentMsat = cursorSent.getLong(0);
    }
    return Math.max(receivedMsat - sentMsat, 0);
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
