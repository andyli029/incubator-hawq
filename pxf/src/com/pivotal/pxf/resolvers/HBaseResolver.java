package com.pivotal.pxf.resolvers;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

import com.pivotal.pxf.exception.BadRecordException;
import com.pivotal.pxf.exception.UnsupportedTypeException;
import com.pivotal.pxf.format.OneField;
import com.pivotal.pxf.format.OneRow;
import com.pivotal.pxf.hadoop.io.GPDBWritable;
import com.pivotal.pxf.utilities.HBaseColumnDescriptor;
import com.pivotal.pxf.utilities.HBaseTupleDescription;
import com.pivotal.pxf.utilities.InputData;
import com.pivotal.pxf.utilities.Plugin;

/*
 * Record resolver for HBase.
 * 
 * The class is responsible to convert rows from HBase scans (returned as Result objects)
 * into a List of OneField objects which later translates into GPDBWritable objects.
 * That also includes the conversion process of each HBase column's value into its GP assigned type.
 *
 * Currently, the class assumes all HBase values are stored as String object Bytes encoded
 */
public class HBaseResolver extends Plugin implements IReadResolver 
{
	private HBaseTupleDescription tupleDescription;

	public HBaseResolver(InputData input) throws Exception
	{
		super(input);
		tupleDescription = new HBaseTupleDescription(input);
	}

	public List<OneField> getFields(OneRow onerow) throws Exception
	{
		Result result = (Result)onerow.getData();
		LinkedList<OneField> fields = new LinkedList<OneField>();

		for (int i = 0; i < tupleDescription.columns(); ++i)
		{
			HBaseColumnDescriptor column = tupleDescription.getColumn(i);
			byte[] value;
			
			if (column.isKeyColumn()) // if a row column is requested
				value = result.getRow(); // just return the row key
			else // else, return column value
				value = getColumnValue(result, column);

			OneField oneField = new OneField();
			oneField.type = column.columnTypeCode();
			oneField.val = convertToJavaObject(oneField.type, column.columnTypeName(), value);
			fields.add(oneField);
		}
		return fields;
	}

	/*
	 * Call the conversion function for type
	 */
	Object convertToJavaObject(int typeCode, String typeName, byte[] val) throws Exception
	{
		if (val == null)
			return null;
        try
        {
            switch(typeCode)
            {
                case GPDBWritable.TEXT:
                case GPDBWritable.VARCHAR:
                case GPDBWritable.BPCHAR:
                    return Bytes.toString(val);

                case GPDBWritable.INTEGER:
                    return Integer.parseInt(Bytes.toString(val));

                case GPDBWritable.BIGINT:
                    return Long.parseLong(Bytes.toString(val));

                case GPDBWritable.SMALLINT:
                    return Short.parseShort(Bytes.toString(val));

                case GPDBWritable.REAL:
                    return Float.parseFloat(Bytes.toString(val));

                case GPDBWritable.FLOAT8:
                    return Double.parseDouble(Bytes.toString(val));

                case GPDBWritable.BYTEA:
                    return val;

                case GPDBWritable.BOOLEAN:
                    return Boolean.valueOf(Bytes.toString(val));
                  
                case GPDBWritable.NUMERIC:
                    return Bytes.toString(val);
                    
                case GPDBWritable.TIMESTAMP:
                    return Timestamp.valueOf(Bytes.toString(val));
                    
                default:
                    throw new UnsupportedTypeException("Unsupported data type " + typeName);
            }
        } 
        catch (NumberFormatException e)
        {
            throw new BadRecordException("Error converting value '" + Bytes.toString(val) + "' " +
            							 "to type " + typeName + ". " +
            							 "(original error: " + e.getMessage() + ")");
        }
	}

	byte[] getColumnValue(Result result, HBaseColumnDescriptor column)
	{
		// if column does not contain a value, return null
		if (!result.containsColumn(column.columnFamilyBytes(),
								   column.qualifierBytes()))
			return null;

		// else, get the latest version of the requested column
		KeyValue keyvalue = result.getColumnLatest(column.columnFamilyBytes(),
												   column.qualifierBytes());
		return keyvalue.getValue();
	}
}