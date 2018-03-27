/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    if (oldVersion < 4) {
      // adds the preimage and recipient column to payment (one alter at a time)
      db.execSQL("ALTER TABLE " + PaymentDao.TABLENAME +
        " ADD COLUMN " + PaymentDao.Properties.Preimage.columnName + " TEXT");
      db.execSQL("ALTER TABLE " + PaymentDao.TABLENAME +
        " ADD COLUMN " + PaymentDao.Properties.Recipient.columnName + " TEXT");
    }
    if (oldVersion < 5) {
      // adds the amount sent column (because it can be different from amount requested)
      db.execSQL("ALTER TABLE " + PaymentDao.TABLENAME +
        " ADD COLUMN " + PaymentDao.Properties.AmountSentMsat.columnName + " INTEGER");
    }
  }
}
