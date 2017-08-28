package fr.acinq.eclair.wallet;


import android.content.Context;

import org.greenrobot.greendao.database.Database;

import fr.acinq.eclair.wallet.models.DaoMaster;
import fr.acinq.eclair.wallet.models.PaymentDao;

/**
 * Helper to handle DB structure changes.
 * Updates the database by applying SQL patches, instead of dropping all tables (default behaviour).
 */
public class DBMigrationHelper extends DaoMaster.OpenHelper {
  public DBMigrationHelper(Context context, String name) {
    super(context, name);
  }

  @Override
  public void onUpgrade(Database db, int oldVersion, int newVersion) {
    // order of patches matters!
    if (oldVersion < 3) {
      // adds a direction column to payment
      db.execSQL("ALTER TABLE " + PaymentDao.TABLENAME +
        " ADD COLUMN " + PaymentDao.Properties.Direction.columnName + " TEXT");
    }
  }
}
