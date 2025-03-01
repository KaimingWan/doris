// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

suite("test_conditional_function", "query") {
    qt_sql "set enable_vectorized_engine = true;"
    qt_sql "set batch_size = 4096;"

    def tbName = "test_conditional_function"
    sql "DROP TABLE IF EXISTS ${tbName};"
    sql """
            CREATE TABLE IF NOT EXISTS ${tbName} (
                user_id INT
            )
            DISTRIBUTED BY HASH(user_id) BUCKETS 5 properties("replication_num" = "1");
        """
    sql """
        INSERT INTO ${tbName} VALUES 
            (1),
            (2),
            (3),
            (4);
        """
    qt_sql "select user_id, case user_id when 1 then 'user_id = 1' when 2 then 'user_id = 2' else 'user_id not exist' end test_case from ${tbName} order by user_id;"
    qt_sql "select user_id, case when user_id = 1 then 'user_id = 1' when user_id = 2 then 'user_id = 2' else 'user_id not exist' end test_case from ${tbName} order by user_id;"

    qt_sql "select user_id, if(user_id = 1, \"true\", \"false\") test_if from ${tbName} order by user_id;"

    sql "DROP TABLE ${tbName};"

    qt_sql "select coalesce(NULL, '1111', '0000');"

    qt_sql "select ifnull(1,0);"
    qt_sql "select ifnull(null,10);"

    qt_sql "select nullif(1,1);"
    qt_sql "select nullif(1,0);"

}
