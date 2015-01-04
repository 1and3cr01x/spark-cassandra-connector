package com.datastax.spark.connector.japi;

import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Row;
import com.datastax.driver.scala.core.TableDef;
import com.datastax.driver.scala.core.io.RowReader;
import com.datastax.driver.scala.core.io.RowReaderFactory;
import com.datastax.driver.scala.core.io.RowReaderOptions;
import scala.Option;
import scala.collection.Seq;

public class GenericJavaRowReaderFactory {
    public final static RowReaderFactory<CassandraRow> instance = new RowReaderFactory<CassandraRow>() {
        @Override
        public RowReader<CassandraRow> rowReader(TableDef table, RowReaderOptions options) {
            return JavaRowReader.instance;
        }

        @Override
        public RowReaderOptions rowReader$default$2() {
            return new RowReaderOptions(RowReaderOptions.apply$default$1());
        }

        @Override
        public Class<CassandraRow> targetClass() {
            return CassandraRow.class;
        }
    };


    public static class JavaRowReader implements RowReader<CassandraRow> {
        public final static JavaRowReader instance = new JavaRowReader();

        private JavaRowReader() {
        }

        @Override
        public CassandraRow read(Row row, String[] columnNames, ProtocolVersion protocolVersion) {
            assert row.getColumnDefinitions().size() == columnNames.length :
                    "Number of columns in a row must match the number of columns in the table metadata";
            return CassandraRow$.MODULE$.fromJavaDriverRow(row, columnNames, protocolVersion);
        }

        @Override
        public Option<Seq<String>> columnNames() {
            return Option.empty();
        }

        @Override
        public Option<Object> requiredColumns() {
            return Option.empty();
        }

        @Override
        public Option<Object> consumedColumns() {
            return Option.empty();
        }
    }

}
