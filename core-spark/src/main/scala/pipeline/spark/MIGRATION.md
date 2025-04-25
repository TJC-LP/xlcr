# Migrating to License-Aware UDFs

This document outlines how to migrate existing steps to use the new license-aware UDF architecture which provides seamless Aspose license management across Spark clusters.

## Overview of Changes

The new architecture:

1. Automatically broadcasts Aspose licenses to all workers
2. Ensures each worker initializes licenses exactly once
3. Provides enhanced lineage tracking
4. Abstracts all license management from step implementations

## Migration Steps

### Step 1: Update Imports

Make sure your step imports the SparkStep trait, which now extends LicenseAwareUdfWrapper:

```scala
import com.tjclp.xlcr.pipeline.spark.SparkStep
```

### Step 2: Convert UDF Definition

Change from this pattern:

```scala
// Old pattern - defined as a val
private val myUdf = wrapUdf2(name, rowTimeout) { (arg1, arg2) =>
  // implementation
}
```

To this pattern:

```scala
// New pattern - defined as a method
private def createMyUdf(implicit spark: SparkSession) = licenseAwareUdf2(name, rowTimeout) { (arg1, arg2) =>
  // implementation
}
```

### Step 3: Update UDF Usage

Change from direct usage:

```scala
override def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
  // Directly use the UDF defined as a val
  df.withColumn("result", myUdf(F.col("input")))
}
```

To instantiation in the transform method:

```scala
override def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
  // Create the UDF each time, passing the SparkSession implicitly
  val myUdf = createMyUdf
  df.withColumn("result", myUdf(F.col("input")))
}
```

### Step 4: Simplify Lineage Parameters

The license-aware UDFs automatically inject Aspose license status information to the lineage when an Aspose implementation is detected. You can simplify your implementation by removing any manual Aspose status tracking:

```scala
// Before - manual license status checking
if (isAsposeBridge) {
  if (SparkPipelineRegistry.isAsposeEnabled) {
    val licenseStatus = SparkPipelineRegistry.getAsposeLicenseStatus
    licenseStatus.foreach { case (k, v) => paramsBuilder.put(k, v) }
  } else {
    paramsBuilder.put("asposeStatus", "disabled")
  }
}

// After - automatically handled by the licenseAwareUdf wrapper
// No need to manually track license status
```

## Example: Converting ConvertStep

Here's an example for ConvertStep:

```scala
// Old implementation
private val convertUdf = wrapUdf2(name, rowTimeout) {
  (bytes: Array[Byte], mimeStr: String) => 
    // ...implementation
}

// New implementation
private def createConvertUdf(implicit spark: SparkSession) = licenseAwareUdf2(name, rowTimeout) {
  (bytes: Array[Byte], mimeStr: String) => 
    // ...implementation
}

override def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
  val convertUdf = createConvertUdf
  // Use the UDF
}
```

## Benefits of Migration

1. **Cluster-Friendly:** Works with any Spark deployment environment
2. **No Worker Configuration:** No need to set environment variables on workers
3. **Detailed Lineage:** More accurate tracking of what happened in the pipeline
4. **License Independence:** Steps don't need to know about license implementation
5. **Maintainability:** Centralized license management logic
6. **Performance:** License data is broadcast once and cached on workers

## Compatibility Notes

The existing `wrapUdf` and `wrapUdf2` methods will continue to work but won't have license-awareness. We recommend migrating to the new pattern for all steps, especially those that may use Aspose components.