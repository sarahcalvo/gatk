package org.broadinstitute.hellbender.tools.sv.cluster;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.StructuralVariantType;
import htsjdk.variant.vcf.VCFConstants;
import org.broadinstitute.hellbender.tools.spark.sv.utils.GATKSVVCFConstants;
import org.broadinstitute.hellbender.tools.sv.SVCallRecord;
import org.broadinstitute.hellbender.tools.sv.SVCallRecordUtils;
import org.broadinstitute.hellbender.tools.sv.SVTestUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SVCollapserTest {

    private static final SVCollapser collapser = new SVCollapser(SVCollapser.BreakpointSummaryStrategy.MEDIAN_START_MEDIAN_END);
    private static final SVCollapser collapserMinMax = new SVCollapser(SVCollapser.BreakpointSummaryStrategy.MIN_START_MAX_END);
    private static final SVCollapser collapserMaxMin = new SVCollapser(SVCollapser.BreakpointSummaryStrategy.MAX_START_MIN_END);
    private static final SVCollapser collapserMean = new SVCollapser(SVCollapser.BreakpointSummaryStrategy.MEAN_START_MEAN_END);
    private static final SVCollapser.AlleleCollectionCollapserComparator alleleComparator = new SVCollapser.AlleleCollectionCollapserComparator();

    private static final Allele MEI_INSERTION_ALLELE = Allele.create("<INS:MEI>");
    private static final Allele SVA_INSERTION_ALLELE = Allele.create("<INS:SVA>");

    @DataProvider(name = "ploidyTestData")
    public Object[][] ploidyTestData() {
        return new Object[][]{
                // Result should be max value
                {new int[]{0}, 0},
                {new int[]{1, 0, 1}, 1},
                {new int[]{2, 1, 1}, 2},
                {new int[]{2, 2, 3, 1}, 3}
        };
    }

    @Test(dataProvider= "ploidyTestData")
    public void testCollapsePloidy(final int[] ploidy, final int result) {
        final Collection<Genotype> genotypes = IntStream.of(ploidy).mapToObj(p -> SVTestUtils.buildHomGenotypeWithPloidy(Allele.REF_N, p)).collect(Collectors.toList());
        Assert.assertEquals(collapser.collapsePloidy(genotypes), result);
    }

    @DataProvider(name = "collapseRefAllelesTestData")
    public Object[][] collapseRefAllelesTestData() {
        return new Object[][]{
                // Empty input should give null
                {Collections.emptyList(), StructuralVariantType.DEL, null},
                // Result should be input for single unique value case
                {Collections.singletonList(Allele.SV_SIMPLE_DEL), StructuralVariantType.DEL, null},
                {Collections.singletonList(Allele.REF_A), StructuralVariantType.DEL, Allele.REF_A},
                {Collections.singletonList(Allele.REF_N), StructuralVariantType.DEL, Allele.REF_N},
                {Collections.singletonList(Allele.NO_CALL), StructuralVariantType.DEL, null},
                {Lists.newArrayList(Allele.REF_A, Allele.REF_A), StructuralVariantType.DUP, Allele.REF_A},
                {Lists.newArrayList(Allele.REF_T, Allele.NO_CALL), StructuralVariantType.DEL, Allele.REF_T},
                {Lists.newArrayList(Allele.REF_N, Allele.NO_CALL), StructuralVariantType.DEL, Allele.REF_N},
                // Result should be N for multiple case
                {Lists.newArrayList(Allele.REF_A, Allele.REF_C), StructuralVariantType.DEL, Allele.REF_N},
                {Lists.newArrayList(Allele.REF_T, Allele.REF_N), StructuralVariantType.DEL, Allele.REF_N},
        };
    }

    @Test(dataProvider= "collapseRefAllelesTestData")
    public void testCollapseRefAlleles(final List<Allele> alleles, final StructuralVariantType svtype, final Allele result) {
        final List<Allele> variantAlleles = alleles.stream().distinct().collect(Collectors.toList());
        final List<SVCallRecord> records = alleles.stream()
                .map(a -> SVTestUtils.newCallRecordWithAlleles(Collections.singletonList(a), Collections.singletonList(a), svtype))
                .collect(Collectors.toList());
        Assert.assertEquals(collapser.collapseRefAlleles(records), result);
    }

    @DataProvider(name = "getSymbolicAlleleBaseSymbolTestData")
    public Object[][] getSymbolicAlleleBaseSymbolTestData() {
        return new Object[][]{
                {Allele.create("<DUP>"), "DUP"},
                {Allele.create("<DUP:TANDEM>"), "DUP"},
                {Allele.create("<INS:MEI:LINE>"), "INS"},
        };
    }

    @Test(dataProvider= "getSymbolicAlleleBaseSymbolTestData")
    public void getSymbolicAlleleBaseSymbolTest(final Allele allele, final String result) {
        Assert.assertEquals(SVCollapser.getSymbolicAlleleBaseSymbol(allele), result);
    }

    @DataProvider(name = "collapseAltAllelesTestData")
    public Object[][] collapseAltAllelesTestData() {
        return new Object[][]{
                {Collections.singletonList(Collections.emptyList()), StructuralVariantType.DEL, Collections.emptyList()},
                {Collections.singletonList(Collections.singletonList(Allele.REF_N)), StructuralVariantType.DEL, Collections.emptyList()},
                {Collections.singletonList(Collections.singletonList(Allele.REF_A)), StructuralVariantType.DEL, Collections.emptyList()},
                {Collections.singletonList(Collections.singletonList(Allele.NO_CALL)), StructuralVariantType.DEL, Collections.emptyList()},
                {Collections.singletonList(Collections.singletonList(Allele.ALT_A)), StructuralVariantType.INS, Collections.singletonList(Allele.ALT_A)},
                {Collections.singletonList(Collections.singletonList(Allele.SV_SIMPLE_DEL)), StructuralVariantType.DEL, Collections.singletonList(Allele.SV_SIMPLE_DEL)},
                {Lists.newArrayList(Collections.singletonList(Allele.REF_N), Collections.singletonList(Allele.SV_SIMPLE_DEL)), StructuralVariantType.DEL, Collections.singletonList(Allele.SV_SIMPLE_DEL)},
                {Lists.newArrayList(Lists.newArrayList(Allele.REF_N, Allele.REF_N), Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)), StructuralVariantType.DEL, Collections.singletonList(Allele.SV_SIMPLE_DEL)},
                {Lists.newArrayList(Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DUP), Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)), StructuralVariantType.CNV, Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP)},
                {Lists.newArrayList(Collections.singletonList(MEI_INSERTION_ALLELE), Collections.singletonList(MEI_INSERTION_ALLELE)), StructuralVariantType.INS, Collections.singletonList(MEI_INSERTION_ALLELE)},
                {Lists.newArrayList(Collections.singletonList(Allele.SV_SIMPLE_INS), Collections.singletonList(MEI_INSERTION_ALLELE)), StructuralVariantType.INS, Collections.singletonList(MEI_INSERTION_ALLELE)},
                {Lists.newArrayList(Collections.singletonList(Allele.SV_SIMPLE_INS), Collections.singletonList(MEI_INSERTION_ALLELE)), StructuralVariantType.INS, Collections.singletonList(MEI_INSERTION_ALLELE)},
                {Lists.newArrayList(Collections.singletonList(MEI_INSERTION_ALLELE), Collections.singletonList(SVA_INSERTION_ALLELE)), StructuralVariantType.INS, Collections.singletonList(Allele.SV_SIMPLE_INS)},
        };
    }

    @Test(dataProvider= "collapseAltAllelesTestData")
    public void collapseAltAllelesTest(final List<List<Allele>> recordGenotypeAlleles,
                                       final StructuralVariantType svtype,
                                       final List<Allele> result) {
        final List<Allele> variantAlleles = recordGenotypeAlleles.stream().flatMap(List::stream).distinct().collect(Collectors.toList());
        final List<SVCallRecord> records = recordGenotypeAlleles.stream()
                .map(a -> SVTestUtils.newCallRecordWithAlleles(a, variantAlleles, svtype))
                .collect(Collectors.toList());
        final List<Allele> sortedTest = SVCallRecordUtils.sortAlleles(collapser.collapseAltAlleles(records, svtype));
        final List<Allele> sortedExpected = SVCallRecordUtils.sortAlleles(result);
        Assert.assertEquals(sortedTest, sortedExpected);
    }

    @DataProvider(name = "collapseSampleAllelesTestData")
    public Object[][] collapseSampleAllelesTestData() {
        return new Object[][]{
                // empty
                {
                        Collections.singletonList(
                                Collections.emptyList()
                        ),
                        Collections.emptyList(),
                        Collections.emptyList()
                },
                // null
                {
                        Collections.singletonList(
                                Collections.singletonList(null)
                        ),
                        Collections.emptyList(),
                        Collections.singletonList(Allele.REF_N)
                },
                // REF
                {
                        Collections.singletonList(
                                Collections.singletonList(Allele.REF_N)
                        ),
                        Collections.emptyList(),
                        Collections.singletonList(Allele.REF_N)
                },
                // DEL
                {
                    Collections.singletonList(
                            Collections.singletonList(Allele.SV_SIMPLE_DEL)
                    ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL)
                },
                // DEL, DEL
                {
                        Lists.newArrayList(
                                Collections.singletonList(Allele.SV_SIMPLE_DEL),
                                Collections.singletonList(Allele.SV_SIMPLE_DEL)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL)
                },
                // DEL, REF
                {
                        Lists.newArrayList(
                                Collections.singletonList(Allele.SV_SIMPLE_DEL),
                                Collections.singletonList(Allele.REF_N)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL)
                },
                // REF/DEL, REF/REF
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.REF_N, Allele.REF_N)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)
                },
                // DEL/DEL, REF/REF
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.REF_N, Allele.REF_N)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL)
                },
                // REF/DEL, REF/DEL
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)
                },
                // REF/DEL, REF/DEL, DEL/DEL
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)
                },
                // REF/DEL, DEL/DEL, DEL/DEL
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL)
                },
                // REF/DEL, REF
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL),
                                Collections.singletonList(Allele.REF_N)
                        ),
                        Collections.singletonList(Allele.SV_SIMPLE_DEL),
                        Lists.newArrayList(Allele.REF_N, Allele.SV_SIMPLE_DEL)
                },
                // DUP/DEL, REF/REF
                {
                        Lists.newArrayList(
                                Lists.newArrayList(Allele.SV_SIMPLE_DUP, Allele.SV_SIMPLE_DEL),
                                Lists.newArrayList(Allele.REF_N, Allele.REF_N)
                        ),
                        Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP),
                        Lists.newArrayList(Allele.SV_SIMPLE_DUP, Allele.SV_SIMPLE_DEL)
                },
        };
    }

    @Test(dataProvider= "collapseSampleAllelesTestData")
    public void collapseSampleAllelesTest(final List<List<Allele>> alleles,
                                          final List<Allele> sampleAltAlleles,
                                          final List<Allele> result) {
        final Collection<Genotype> genotypes = alleles.stream().map(a -> new GenotypeBuilder().alleles(a).make()).collect(Collectors.toList());
        final List<Allele> sortedTest = SVCallRecordUtils.sortAlleles(collapser.collapseSampleAlleles(genotypes, Allele.REF_N, sampleAltAlleles));
        final List<Allele> sortedResult = SVCallRecordUtils.sortAlleles(result);
        Assert.assertEquals(sortedTest, sortedResult);
    }

    @DataProvider(name = "collapseAttributesTestData")
    public Object[][] collapseAttributesTestData() {
        return new Object[][]{
                // Null value
                {
                        Collections.singletonList("var1"),
                        Collections.singletonList(new String[]{VCFConstants.GENOTYPE_QUALITY_KEY}),
                        Collections.singletonList(new Object[]{null}),
                        new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                        new Object[]{null}
                },
                // Single key / value
                {
                        Collections.singletonList("var1"),
                        Collections.singletonList(new String[]{VCFConstants.GENOTYPE_QUALITY_KEY}),
                        Collections.singletonList(new Object[]{30}),
                        new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                        new Object[]{30}
                },
                // Two samples, null values
                {
                        Lists.newArrayList("var1", "var2"),
                        Lists.newArrayList(
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY}
                        ),
                        Lists.newArrayList(
                                new Object[]{null},
                                new Object[]{null}),
                        new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                        new Object[]{null}
                },
                // Two samples, same key/value
                {
                        Lists.newArrayList("var1", "var2"),
                        Lists.newArrayList(
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY}
                                ),
                        Lists.newArrayList(
                                new Object[]{30},
                                new Object[]{30}),
                        new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                        new Object[]{30}
                },
                // Two samples, same key / different value
                {
                        Lists.newArrayList("var1", "var2"),
                        Lists.newArrayList(
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY}
                        ),
                        Lists.newArrayList(
                                new Object[]{30},
                                new Object[]{45}),
                        new String[]{VCFConstants.GENOTYPE_QUALITY_KEY},
                        new Object[]{null}
                },
                // Two samples, one with an extra key
                {
                        Lists.newArrayList("var1", "var2"),
                        Lists.newArrayList(
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY, "KEY2"},
                                new String[]{VCFConstants.GENOTYPE_QUALITY_KEY}
                        ),
                        Lists.newArrayList(
                                new Object[]{30, "VALUE2"},
                                new Object[]{30}),
                        new String[]{VCFConstants.GENOTYPE_QUALITY_KEY, "KEY2"},
                        new Object[]{30, "VALUE2"}
                }
        };
    }

    @Test(dataProvider= "collapseAttributesTestData")
    public void collapseAttributesTest(final List<String> variantIds,
                                       final List<String[]> keys,
                                       final List<Object[]> values,
                                       final String[] expectedKeys,
                                       final Object[] expectedValues) {
        final List<Map<String, Object>> inputAttributesList = IntStream.range(0, keys.size())
                .mapToObj(i -> SVTestUtils.keyValueArraysToMap(keys.get(i), values.get(i)))
                .collect(Collectors.toList());
        final Map<String, Object> expectedAttributes = SVTestUtils.keyValueArraysToMap(expectedKeys, expectedValues);

        // Test as genotype attributes
        final List<Genotype> genotypes = inputAttributesList.stream()
                .map(m -> new GenotypeBuilder().attributes(m).make())
                .collect(Collectors.toList());
        Assert.assertEquals(collapser.collapseGenotypeAttributes(genotypes), expectedAttributes);

        // Test as variant attributes
        final List<SVCallRecord> variants = IntStream.range(0, inputAttributesList.size())
                .mapToObj(i -> SVTestUtils.newNamedDeletionRecordWithAttributes(variantIds.get(i), inputAttributesList.get(i)))
                .collect(Collectors.toList());
        final Map<String, Object> expectedAttributesWithMembers = new HashMap<>(expectedAttributes);
        expectedAttributesWithMembers.put(GATKSVVCFConstants.CLUSTER_MEMBER_IDS_KEY, variantIds);
        Assert.assertEquals(collapser.collapseVariantAttributes(variants), expectedAttributesWithMembers);
    }

    @DataProvider(name = "collapseLengthTestData")
    public Object[][] collapseLengthTestData() {
        return new Object[][]{
                {
                        new int[]{1000},
                        new String[]{"chr1"},
                        new StructuralVariantType[]{StructuralVariantType.DEL},
                        1,
                        1000,
                        StructuralVariantType.DEL,
                        1000
                },
                {
                        new int[]{1000, 1000},
                        new String[]{"chr1", "chr1"},
                        new StructuralVariantType[]{StructuralVariantType.DUP, StructuralVariantType.DUP},
                        1,
                        1000,
                        StructuralVariantType.DUP,
                        1000
                },
                {
                        new int[]{300, 400},
                        new String[]{"chr1", "chr1"},
                        new StructuralVariantType[]{StructuralVariantType.DEL, StructuralVariantType.DUP},
                        1001,
                        1350,
                        StructuralVariantType.CNV,
                        350
                },
                {
                        new int[]{300, 400},
                        new String[]{"chr1", "chr1"},
                        new StructuralVariantType[]{StructuralVariantType.INS, StructuralVariantType.INS},
                        1,
                        1,
                        StructuralVariantType.INS,
                        350
                },
                {
                        new int[]{300, 400, 500},
                        new String[]{"chr1", "chr1", "chr1"},
                        new StructuralVariantType[]{StructuralVariantType.INS, StructuralVariantType.INS, StructuralVariantType.INS},
                        1,
                        1,
                        StructuralVariantType.INS,
                        400
                },
                {
                        new int[]{-1},
                        new String[]{"chr2"},
                        new StructuralVariantType[]{StructuralVariantType.BND},
                        1,
                        1,
                        StructuralVariantType.BND,
                        -1
                }
        };
    }

    @Test(dataProvider= "collapseLengthTestData")
    public void collapseLengthTest(final int[] lengths, final String[] chrom2, final StructuralVariantType[] svtypes,
                                   final int newStart, final int newEnd, final StructuralVariantType newType, final int expectedLength) {
        final List<SVCallRecord> records = IntStream.range(0, lengths.length).mapToObj(i -> SVTestUtils.newCallRecordWithLengthAndTypeAndChrom2(lengths[i], svtypes[i], chrom2[i])).collect(Collectors.toList());
        Assert.assertEquals(collapser.collapseLength(records, newStart, newEnd, newType), expectedLength);
    }

    @DataProvider(name = "collapseIdsTestData")
    public Object[][] collapseIdsTestData() {
        return new Object[][]{
                {Collections.singletonList("var1"), "var1"},
                {Lists.newArrayList("var1", "var2"), "var1"},
                {Lists.newArrayList("var2", "var1"), "var1"},
        };
    }

    @Test(dataProvider= "collapseIdsTestData")
    public void collapseIdsTest(final List<String> ids, final String expectedResult) {
        final List<SVCallRecord> records = ids.stream().map(SVTestUtils::newDeletionCallRecordWithId).collect(Collectors.toList());
        final String result = collapser.collapseIds(records);
        Assert.assertEquals(result, expectedResult);
    }

    @DataProvider(name = "getMostPreciseCallsTestData")
    public Object[][] getMostPreciseCallsTestData() {
        return new Object[][]{
                {
                        new String[]{"depth1"},
                        Collections.singletonList(
                                Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM)
                        ),
                        Sets.newHashSet("depth1")
                },
                {
                        new String[]{"depth1", "depth2"},
                        Lists.newArrayList(
                                Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM),
                                Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM)
                        ),
                        Sets.newHashSet("depth1", "depth2")
                },
                {
                        new String[]{"pesr1"},
                        Collections.singletonList(
                                Collections.singletonList("pesr")
                        ),
                        Sets.newHashSet("pesr1")
                },
                {
                        new String[]{"pesr1", "pesr2"},
                        Lists.newArrayList(
                                Collections.singletonList("pesr"),
                                Collections.singletonList("pesr")
                        ),
                        Sets.newHashSet("pesr1", "pesr2")
                },
                {
                        new String[]{"depth1", "pesr1"},
                        Lists.newArrayList(
                                Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM),
                                Collections.singletonList("pesr")
                        ),
                        Sets.newHashSet("pesr1"),
                },
                {
                        new String[]{"mixed1", "depth1"},
                        Lists.newArrayList(
                                Lists.newArrayList(GATKSVVCFConstants.DEPTH_ALGORITHM, "pesr"),
                                Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM)
                        ),
                        Sets.newHashSet("mixed1"),
                },
                {
                        new String[]{"mixed1", "pesr1"},
                        Lists.newArrayList(
                                Lists.newArrayList(GATKSVVCFConstants.DEPTH_ALGORITHM, "pesr"),
                                Collections.singletonList("pesr")
                        ),
                        Sets.newHashSet("mixed1", "pesr1"),
                },
        };
    }

    @Test(dataProvider= "getMostPreciseCallsTestData")
    public void getMostPreciseCallsTest(final String[] ids, final List<List<String>> algorithms, final Set<String> expectedIds) {
        final List<SVCallRecord> records = IntStream.range(0, ids.length).mapToObj(i -> SVTestUtils.newDeletionCallRecordWithIdAndAlgorithms(ids[i], algorithms.get(i))).collect(Collectors.toList());
        final List<String> resultIds = collapser.getMostPreciseCalls(records).stream().map(SVCallRecord::getId).collect(Collectors.toList());
        Assert.assertEquals(resultIds.size(), expectedIds.size());
        Assert.assertTrue(expectedIds.containsAll(resultIds));
    }

    @DataProvider(name = "collapseIntervalTestData")
    public Object[][] collapseIntervalTestData() {
        return new Object[][]{
                // 1 variant
                {
                        new int[]{1001}, // starts
                        new int[]{1100}, // ends
                        StructuralVariantType.DEL,
                        new int[]{1001, 1100}, // median strategy
                        new int[]{1001, 1100}, // min-max strategy
                        new int[]{1001, 1100}, // max-min strategy
                        new int[]{1001, 1100}  // mean strategy
                },
                // 2 variants
                {
                        new int[]{1001, 1011},
                        new int[]{1100, 1110},
                        StructuralVariantType.DEL,
                        new int[]{1011, 1110},
                        new int[]{1001, 1110},
                        new int[]{1011, 1100},
                        new int[]{1006, 1105}
                },
                // 3 variants
                {
                        new int[]{1001, 1011, 1021},
                        new int[]{1100, 1110, 1121},
                        StructuralVariantType.DUP,
                        new int[]{1011, 1110},
                        new int[]{1001, 1121},
                        new int[]{1021, 1100},
                        new int[]{1011, 1110}
                },
                // BND
                {
                        new int[]{1001, 1011, 1021},
                        new int[]{1100, 1110, 1121},
                        StructuralVariantType.BND,
                        new int[]{1011, 1110},
                        new int[]{1001, 1121},
                        new int[]{1021, 1100},
                        new int[]{1011, 1110}
                },
                // INS, same start/end
                {
                        new int[]{1001, 1011, 1021},
                        new int[]{1001, 1011, 1021},
                        StructuralVariantType.INS,
                        new int[]{1011, 1011},
                        new int[]{1011, 1011},
                        new int[]{1011, 1011},
                        new int[]{1011, 1011}
                },
                // INS, different start/end
                {
                        new int[]{1001, 1011, 1021},
                        new int[]{1011, 1021, 1031},
                        StructuralVariantType.INS,
                        new int[]{1016, 1016},
                        new int[]{1016, 1016},
                        new int[]{1016, 1016},
                        new int[]{1016, 1016}
                }
        };
    }

    @Test(dataProvider= "collapseIntervalTestData")
    public void collapseIntervalTest(final int[] starts, final int[] ends, final StructuralVariantType svtype,
                                     final int[] expectedMedian, final int[] expectedMinMax, final int[] expectedMaxMin,
                                     final int[] expectedMean) {
        final List<SVCallRecord> records =  IntStream.range(0, starts.length)
                .mapToObj(i -> SVTestUtils.newCallRecordWithIntervalAndType(starts[i], ends[i], svtype)).collect(Collectors.toList());

        final Map.Entry<Integer, Integer> resultMedian = collapser.collapseInterval(records);
        Assert.assertEquals((int) resultMedian.getKey(), expectedMedian[0]);
        Assert.assertEquals((int) resultMedian.getValue(), expectedMedian[1]);

        final Map.Entry<Integer, Integer> resultMinMax = collapserMinMax.collapseInterval(records);
        Assert.assertEquals((int) resultMinMax.getKey(), expectedMinMax[0]);
        Assert.assertEquals((int) resultMinMax.getValue(), expectedMinMax[1]);

        final Map.Entry<Integer, Integer> resultMaxMin = collapserMaxMin.collapseInterval(records);
        Assert.assertEquals((int) resultMaxMin.getKey(), expectedMaxMin[0]);
        Assert.assertEquals((int) resultMaxMin.getValue(), expectedMaxMin[1]);

        final Map.Entry<Integer, Integer> resultMean = collapserMean.collapseInterval(records);
        Assert.assertEquals((int) resultMean.getKey(), expectedMean[0]);
        Assert.assertEquals((int) resultMean.getValue(), expectedMean[1]);
    }

    @DataProvider(name = "collapseTypesTestData")
    public Object[][] collapseTypesTestData() {
        return new Object[][]{
                {Collections.singletonList(StructuralVariantType.DEL), StructuralVariantType.DEL},
                {Collections.singletonList(StructuralVariantType.DUP), StructuralVariantType.DUP},
                {Collections.singletonList(StructuralVariantType.INS), StructuralVariantType.INS},
                {Collections.singletonList(StructuralVariantType.INV), StructuralVariantType.INV},
                {Collections.singletonList(StructuralVariantType.BND), StructuralVariantType.BND},
                {Collections.singletonList(StructuralVariantType.CNV), StructuralVariantType.CNV},
                {Lists.newArrayList(StructuralVariantType.DEL, StructuralVariantType.DUP), StructuralVariantType.CNV},
                {Lists.newArrayList(StructuralVariantType.DEL, StructuralVariantType.DEL), StructuralVariantType.DEL},
                {Lists.newArrayList(StructuralVariantType.DUP, StructuralVariantType.DUP), StructuralVariantType.DUP},
                {Lists.newArrayList(StructuralVariantType.INS, StructuralVariantType.INS), StructuralVariantType.INS},
                {Lists.newArrayList(StructuralVariantType.INV, StructuralVariantType.INV), StructuralVariantType.INV},
                {Lists.newArrayList(StructuralVariantType.BND, StructuralVariantType.BND), StructuralVariantType.BND},
                {Lists.newArrayList(StructuralVariantType.CNV, StructuralVariantType.CNV), StructuralVariantType.CNV},
                {Lists.newArrayList(StructuralVariantType.DEL, StructuralVariantType.DUP, StructuralVariantType.CNV), StructuralVariantType.CNV}
        };
    }

    @Test(dataProvider= "collapseTypesTestData")
    public void collapseTypesTest(final List<StructuralVariantType> types, final StructuralVariantType expectedResult) {
        final List<SVCallRecord> records = types.stream().map(t -> SVTestUtils.newCallRecordWithIntervalAndType(1, 100, t)).collect(Collectors.toList());
        Assert.assertEquals(collapser.collapseTypes(records), expectedResult);
    }

    @DataProvider(name = "collapseAlgorithmsTestData")
    public Object[][] collapseAlgorithmsTestData() {
        return new Object[][]{
                {Collections.singletonList(Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM)), Collections.singletonList(GATKSVVCFConstants.DEPTH_ALGORITHM)},
                {Collections.singletonList(Collections.singletonList("pesr")), Collections.singletonList("pesr")},
                {Collections.singletonList(Lists.newArrayList("pesrA", "pesrB")), Lists.newArrayList("pesrA", "pesrB")},
                {Collections.singletonList(Lists.newArrayList("pesrB", "pesrA")), Lists.newArrayList("pesrA", "pesrB")},
                {Lists.newArrayList(Collections.singletonList("pesrA"), Collections.singletonList("pesrB")), Lists.newArrayList("pesrA", "pesrB")},
                {Lists.newArrayList(Collections.singletonList("pesrB"), Collections.singletonList("pesrA")), Lists.newArrayList("pesrA", "pesrB")},
                {Lists.newArrayList(Lists.newArrayList("pesrA", "pesrB"), Collections.singletonList("pesrB")), Lists.newArrayList("pesrA", "pesrB")},
                {Lists.newArrayList(Lists.newArrayList("pesrB", "pesrA"), Collections.singletonList("pesrA")), Lists.newArrayList("pesrA", "pesrB")},
        };
    }

    @Test(dataProvider= "collapseAlgorithmsTestData")
    public void collapseAlgorithmsTest(final List<List<String>> algorithmLists, final List<String> expectedResult) {
        final List<SVCallRecord> records = algorithmLists.stream().map(list -> SVTestUtils.newDeletionCallRecordWithIdAndAlgorithms("", list)).collect(Collectors.toList());
        Assert.assertEquals(collapser.collapseAlgorithms(records), expectedResult);
    }

    @DataProvider(name = "alleleCollapserComparatorTestData")
    public Object[][] alleleCollapserComparatorTestData() {
        return new Object[][]{
                {Collections.emptyList(), Collections.emptyList(), 0},
                {Collections.singletonList(Allele.NO_CALL), Collections.singletonList(Allele.NO_CALL), 0},
                {Collections.singletonList(Allele.REF_N), Collections.singletonList(Allele.REF_N), 0},
                {Collections.singletonList(Allele.SV_SIMPLE_DEL), Collections.singletonList(Allele.SV_SIMPLE_DEL), 0},
                {Lists.newArrayList(Allele.SV_SIMPLE_DUP, Allele.SV_SIMPLE_DUP), Lists.newArrayList(Allele.SV_SIMPLE_DUP, Allele.SV_SIMPLE_DUP), 0},
                // When otherwise equal up to common length, shorter list first should return 1 <=> longer list first should return -1
                {Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL), Collections.singletonList(Allele.SV_SIMPLE_DEL), 1},
                {Collections.singletonList(Allele.SV_SIMPLE_DEL), Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP), -1},
                {Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP), Collections.singletonList(Allele.SV_SIMPLE_DEL), 1},
                {Collections.singletonList(Allele.SV_SIMPLE_DEL), Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP), -1},
                {Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP), Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL), 1},
                {Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL), Lists.newArrayList(Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DEL, Allele.SV_SIMPLE_DUP), -1}
        };
    }

    @Test(dataProvider= "alleleCollapserComparatorTestData")
    public void alleleCollapserComparatorTest(final List<Allele> allelesA, final List<Allele> allelesB, final int expectedResult) {
        Assert.assertEquals(alleleComparator.compare(allelesA, allelesB), expectedResult);
    }
}