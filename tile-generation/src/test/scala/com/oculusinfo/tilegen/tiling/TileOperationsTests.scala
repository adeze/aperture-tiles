/*
 * Copyright (c) 2014 Oculus Info Inc.
 * http://www.oculusinfo.com/
 *
 * Released under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oculusinfo.tilegen.tiling


import java.io.File
import com.oculusinfo.tilegen.datasets.SchemaTypeUtilities
import org.apache.spark.SharedSparkContext
import org.apache.spark.sql.catalyst.types.StructType
import org.scalatest.FunSuite
import scala.collection.mutable.ListBuffer
import com.oculusinfo.binning.util.JSONUtilitiesTests
import org.json.JSONObject
import com.oculusinfo.binning.impl.WebMercatorTilePyramid
import com.oculusinfo.binning.TileIndex
import org.apache.spark.sql.SchemaRDD

class TileOperationsTests extends FunSuite with SharedSparkContext {
	import com.oculusinfo.tilegen.tiling.TileOperations._

	def outputOps(colSpecs: List[String], output: ListBuffer[Any])(input: PipelineData) = {
		val extractors = colSpecs.map(SchemaTypeUtilities.calculateExtractor(_, input.srdd.schema))
		val results = input.srdd.collect().map(row => extractors.map(_(row)))
		output ++= results.toList.flatten
		input
	}

	def outputOp(colSpec: String, output: ListBuffer[Any])(input: PipelineData) = {
		outputOps(List(colSpec), output)(input)
	}

	test("Test load JSON data parse and operation") {
		val resultList = ListBuffer[Any]()

		val resPath = getClass.getResource("/json_test.data").toURI.getPath
		val argsMap = Map("ops.path" -> resPath)

		val loadStage = new PipelineStage("load", parseLoadJsonDataOp(argsMap))
		loadStage.addChild(new PipelineStage("output", outputOps(List("val", "time"), resultList)(_)))
		TilePipelines.execute(loadStage, sqlc)

		assertResult(List(
			             "one", "2015-01-01 10:15:30",
			             "two", "2015-01-02 8:15:30",
			             "three", "2015-01-03 10:15:30"))(resultList.toList)
	}

	test("Test load CSV data parse and operation") {
		val resultList = ListBuffer[Any]()

		val resPath = getClass.getResource("/csv_test.data").toURI.getPath
		val argsMap = Map(
			"ops.path" -> resPath,
			"oculus.binning.parsing.separator" -> " *, *",
			"oculus.binning.parsing.vAl.index" -> "0",
			"oculus.binning.parsing.vAl.fieldType" -> "string", // use mixed case fieldname to test case sensitivity
			"oculus.binning.parsing.num.index" -> "1",
			"oculus.binning.parsing.num.fieldType" -> "long",
			"oculus.binning.parsing.num_1.index" -> "2",
			"oculus.binning.parsing.num_1.fieldType" -> "double",
			"oculus.binning.parsing.time.index" -> "3",
			"oculus.binning.parsing.time.fieldType" -> "string",
			"oculus.binning.parsing.desc.index" -> "4",
			"oculus.binning.parsing.desc.fieldType" -> "string"
		)

		val loadStage = PipelineStage("load", parseLoadCsvDataOp(argsMap))
		loadStage.addChild(PipelineStage("output", outputOps(List("vAl", "time"), resultList)(_)))
		TilePipelines.execute(loadStage, sqlc)

		assertResult(List(
			             "one", "2015-01-01 10:15:30",
			             "two", "2015-01-02 8:15:30",
			             "three", "2015-01-03 10:15:30"))(resultList.toList)
	}

	test("Test cache operation") {
		def checkTableName(count: Int, clearTableName: Boolean)(input: PipelineData) = {
			assertResult(Some(s"cached_table_$count"))(input.tableName)
			if (clearTableName) PipelineData(input.sqlContext, input.srdd) else input
		}

		val argMap = Map("ops.path" -> getClass.getResource("/json_test.data").toURI.getPath)
		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("cache_op_1", parseCacheDataOp(Map.empty)(_)))
			.addChild(PipelineStage("check_cache_op_1", checkTableName(0, true)(_)))
			.addChild(PipelineStage("cache_op_2", parseCacheDataOp(Map.empty)(_)))
			.addChild(PipelineStage("check_cache_op_2", checkTableName(1, false)(_)))
			.addChild(PipelineStage("cache_op_3", parseCacheDataOp(Map.empty)(_)))
			.addChild(PipelineStage("check_cache_op_3", checkTableName(1, false)(_)))
		TilePipelines.execute(rootStage, sqlc)
	}

	test("Test date filter parse and operation") {
		val resultList = ListBuffer[Any]()
		val argMap = Map(
			"ops.path" -> getClass.getResource("/json_test.data").toURI.getPath,
			"ops.column" -> "time",
			"ops.start" -> "2015-01-01 15:15:30",
			"ops.end" -> "2015-01-02 10:15:30",
			"ops.format" -> "yyyy-MM-DD HH:mm:ss")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("date_filter", parseDateFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("time", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List("2015-01-02 8:15:30"))(resultList.toList)
	}

	test("Test mercator filter parse and operation") {
		val resultList = ListBuffer[Any]()
		val pyramid = new WebMercatorTilePyramid
		val bounds = pyramid.getTileBounds(new TileIndex(0, 0, 0))
		// Mercator projection is ok for all X; just bad for Y out of range.
		val rawData = List(SchemaTypeUtilities.row("a", 0.0, 0.0),
		                   SchemaTypeUtilities.row("b", 0.0, bounds.getMinY),
		                   SchemaTypeUtilities.row("c", 0.0, bounds.getMinY-1E-12),
		                   SchemaTypeUtilities.row("d", 0.0, bounds.getMaxY),
		                   SchemaTypeUtilities.row("e", 0.0, bounds.getMaxY-1E-12),
		                   SchemaTypeUtilities.row("f", -181.0, 0.0),
		                   SchemaTypeUtilities.row("g", 181.0, 0.0))
		val data = sqlc.applySchema(
			sc.parallelize(rawData),
			SchemaTypeUtilities.structSchema(SchemaTypeUtilities.schemaField("id", classOf[String]),
			                                 SchemaTypeUtilities.schemaField("lon", classOf[Double]),
			                                 SchemaTypeUtilities.schemaField("lat", classOf[Double])))

		val rootStage = PipelineStage("load", loadRDDOp(data))
		rootStage.addChild(PipelineStage("mercator filter", parseMercatorFilterOp(Map("ops.latitude" -> "lat"))))
			.addChild(PipelineStage("output", outputOp("id", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List("a", "b", "e", "f", "g"))(resultList.toList)
	}

	test("Test integral range filter parse and operation") {
		val resultList = ListBuffer[Any]()
		val argMap = Map(
			"ops.path" -> getClass.getResource("/json_test.data").toURI.getPath,
			"ops.columns" -> "num,num_1",
			"ops.min" -> "2,2",
			"ops.max" -> "3,3")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("range_filter", parseIntegralRangeFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("num", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List(2, 3))(resultList.toList)
	}

	test("Test integral range filter parse and operation with exclusions") {
		val resultList = ListBuffer[Any]()
		val argMap = Map(
			"ops.path" -> getClass.getResource("/json_test.data").toURI.getPath,
			"ops.columns" -> "num,num_1",
			"ops.min" -> "2,2",
			"ops.max" -> "3,3",
			"ops.exclude" -> "true")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("range_filter", parseIntegralRangeFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("num", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List(1))(resultList.toList)
	}

	test("Test fractional range filter parse and operation") {
		val resultList = ListBuffer[Any]()

		getClass.getResource("/json_test.data").toURI.getPath
		val argMap = Map(
			"ops.path" -> getClass.getResource("/json_test.data").toURI.getPath,
			"ops.columns" -> "num,num_1",
			"ops.min" -> "2.0,2.0",
			"ops.max" -> "3.0,3.0")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("range_filter", parseFractionalRangeFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("num", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List(2, 3))(resultList.toList)
	}

	test("Test fractional range filter parse and operation with exclusions") {
		val resultList = ListBuffer[Any]()

		val resPath = getClass.getResource("/json_test.data").toURI.getPath
		val argMap = Map(
			"ops.path" -> resPath,
			"ops.columns" -> "num,num_1",
			"ops.min" -> "2.0,2.0",
			"ops.max" -> "3.0,3.0",
			"ops.exclude" -> "true")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("range_filter", parseFractionalRangeFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("num", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List(1))(resultList.toList)
	}

	test("Test regex filter parse and operation") {
		val resultList = ListBuffer[Any]()

		val resPath = getClass.getResource("/json_test.data").toURI.getPath
		val argMap = Map(
			"ops.path" -> resPath,
			"ops.column" -> "desc",
			"ops.exclude" -> "false",
			"ops.regex" -> """a+b+\d*\..*\w\w""")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("regex_filter", parseRegexFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("desc", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List("aabb.?cc", "ab99.?xx"))(resultList.toList)
	}

	test("Test regex filter parse and operation with exclude") {
		val resultList = ListBuffer[Any]()

		val resPath = getClass.getResource("/json_test.data").toURI.getPath
		val argMap = Map(
			"ops.path" -> resPath,
			"ops.column" -> "desc",
			"ops.exclude" -> "true",
			"ops.regex" -> """a+b+\d*\..*\w\w""")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("regex_filter", parseRegexFilterOp(argMap)))
			.addChild(PipelineStage("output", outputOp("desc", resultList)(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(List("aabcc.?"))(resultList.toList)
	}

	test("Test column select parse and operation") {
		var schema: StructType = null
		def schemaOp()(input: PipelineData) = {
			schema = input.srdd.schema
			input
		}

		val resPath = getClass.getResource("/json_test.data").toURI.getPath
		val argMap = Map(
			"ops.path" -> resPath,
			"ops.columns" -> "val,num")

		val rootStage = PipelineStage("load", parseLoadJsonDataOp(argMap))
		rootStage.addChild(PipelineStage("column_select", parseColumnSelectOp(argMap)))
			.addChild(PipelineStage("output", schemaOp()(_)))

		TilePipelines.execute(rootStage, sqlc)

		assertResult(schema.fields.size)(2)
		assert(schema.fieldNames.contains("val"))
		assert(schema.fieldNames.contains("num"))
	}

	test("Test geo heatmap parse and operation") {
		import scala.collection.JavaConversions._

		try {
			// pipeline stage to create test data
			def createDataOp(count: Int)(input: PipelineData) = {
				val jsonData = for (x <- 0 until count; y <- 0 until count/2) yield {
					val lon = -180.0 + (x / count.toFloat * 360.0)
					val lat = -45.0 + (y  * 90.0 / (count / 2))
					s"""{"x":$lon, "y":$lat, "data":${(x * count + y).toDouble}}\n"""
				}
				val srdd = sqlc.jsonRDD(sc.parallelize(jsonData))
				PipelineData(sqlc, srdd)
			}

			// Run the tile job
			val args = Map(
				"ops.xColumn" -> "x",
				"ops.yColumn" -> "y",
				"ops.name" -> "test",
				"ops.description" -> "a test description",
				"ops.prefix" -> "test_prefix",
				"ops.levels.0" -> "0",
				"ops.tileWidth" -> "4",
				"ops.tileHeight" -> "4",
				"ops.valueColumn" -> "data",
				"ops.valueType" -> "double",
				"ops.aggregationType" -> "sum")

			val rootStage = PipelineStage("create_data", createDataOp(8)(_))
			rootStage.addChild(PipelineStage("geo_heatmap_op", parseGeoHeatMapOp(args)))
			TilePipelines.execute(rootStage, sqlc)

			// Load the metadata and validate its contents - gives us an indication of whether or not the
			// job completed successfully, and if performed the expected operation.  There are more detailed
			// tests for the operations themselves.
			val tileIO = new LocalTileIO("avro")
			val metaData = tileIO.readMetaData("test.x.y.data").getOrElse(fail("Metadata not created"))

			JSONUtilitiesTests.assertJsonEqual(new JSONObject("""{"minimum":0, "maximum":218}"""),
			                                   new JSONObject(metaData.getCustomMetaData("0").toString))
			JSONUtilitiesTests.assertJsonEqual(new JSONObject("""{"minimum":0, "maximum":218}"""),
			                                   new JSONObject(metaData.getCustomMetaData("global").toString))

			val customMeta = metaData.getAllCustomMetaData
			assert(0 === customMeta.get("0.minimum"))
			assert(218 === customMeta.get("0.maximum"))
			assert(0 === customMeta.get("global.minimum"))
			assert(218 === customMeta.get("global.maximum"))
		} finally {
			// Remove the tile set we created
			def removeRecursively (file: File): Unit = {
				if (file.isDirectory) {
					file.listFiles().foreach(removeRecursively)
				}
				file.delete()
			}
			// If you want to look at the tile set (not remove it) comment out this line.
			removeRecursively(new File("test.x.y.data"))
		}
	}

	test("Test crossplot heatmap parse and operation") {
		import scala.collection.JavaConversions._

		try {
			// pipeline stage to create test data
			def createDataOp(count: Int)(input: PipelineData) = {
				val jsonData = for (x <- 0 until count; y <- 0 until count if y % 2 == 0) yield {
					s"""{"x":$x, "y":$y, "data":${(x * count + y)}}\n"""
				}
				val srdd = sqlc.jsonRDD(sc.parallelize(jsonData))
				PipelineData(sqlc, srdd)
			}

			// Run the tile job
			val args = Map(
				"ops.xColumn" -> "x",
				"ops.yColumn" -> "y",
				"ops.name" -> "test",
				"ops.description" -> "a test description",
				"ops.prefix" -> "test_prefix",
				"ops.levels.0" -> "0",
				"ops.tileWidth" -> "4",
				"ops.tileHeight" -> "4",
				"ops.valueColumn" -> "data",
				"ops.valueType" -> "int",
				"ops.aggregationType" -> "sum",
				"ops.minX" -> "0.0",
				"ops.minY" -> "0.0",
				"ops.maxX" -> "7.0",
				"ops.maxY" -> "7.0")

			val rootStage = PipelineStage("create_data", createDataOp(8)(_))
			rootStage.addChild(PipelineStage("crossplot_heatmap_op", parseCrossplotHeatmapOp(args)))
			TilePipelines.execute(rootStage, sqlc)

			// Load the metadata and validate its contents - gives us an indication of whether or not the
			// job completed successfully, and if performed the expected operation.  There are more detailed
			// tests for the operations themselves.
			val tileIO = new LocalTileIO("avro")
			val metaData = tileIO.readMetaData("test.x.y.data").getOrElse(fail("Metadata not created"))

			val bounds = metaData.getBounds
			assertResult(0.0)(bounds.getMinX)
			assertResult(0.0)(bounds.getMinY)
			assertResult(7.0)(bounds.getMaxX)
			assertResult(6.0)(bounds.getMaxY)

			JSONUtilitiesTests.assertJsonEqual(new JSONObject("""{"minimum":0, "maximum":84}"""),
			                                   new JSONObject(metaData.getCustomMetaData("0").toString))
			JSONUtilitiesTests.assertJsonEqual(new JSONObject("""{"minimum":0, "maximum":84}"""),
			                                   new JSONObject(metaData.getCustomMetaData("global").toString))

			val customMeta = metaData.getAllCustomMetaData
			assert(0 === customMeta.get("0.minimum"))
			assert(84 === customMeta.get("0.maximum"))
			assert(0 === customMeta.get("global.minimum"))
			assert(84 === customMeta.get("global.maximum"))
		} finally {
			// Remove the tile set we created
			def removeRecursively (file: File): Unit = {
				if (file.isDirectory) {
					file.listFiles().foreach(removeRecursively)
				}
				file.delete()
			}
			// If you want to look at the tile set (not remove it) comment out this line.
			removeRecursively(new File("test.x.y.data"))
		}
	}
}
