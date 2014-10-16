package com.datastax.spark.connector.japi;

import com.datastax.driver.core.Row;
import com.datastax.spark.connector.cql.TableDef;
import com.datastax.spark.connector.rdd.reader.RowReader;
import com.datastax.spark.connector.rdd.reader.RowReaderFactory;
import com.datastax.spark.connector.rdd.reader.RowReaderOptions;
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
        public CassandraRow read(Row row, String[] columnNames) {
            assert row.getColumnDefinitions().size() == columnNames.length :
                    "Number of columns in a row must match the number of columns in the table metadata";
            return CassandraRow$.MODULE$.fromJavaDriverRow(row, columnNames);
        }

        @Override
        public Option<Seq<String>> columnNames() {
            return Option.empty();
        }

        @Override
        public Option<Object> columnCount() {
            return Option.empty();
        }

        @Override
        public Option<Object> consecutiveColumns() {
            return Option.empty();
        }
    }

}
