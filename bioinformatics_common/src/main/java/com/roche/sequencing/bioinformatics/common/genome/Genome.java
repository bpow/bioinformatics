package com.roche.sequencing.bioinformatics.common.genome;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.roche.sequencing.bioinformatics.common.sequence.ISequence;
import com.roche.sequencing.bioinformatics.common.sequence.SimpleNucleotideCodeSequence;
import com.roche.sequencing.bioinformatics.common.utils.StringUtil;

public class Genome {

	private final static int LONG_LENGTH_IN_BYTES = 8;
	private final static int CONTAINER_NAME_INDEX_IN_CONTAINER_INFORMATION = 0;
	private final static int CONTAINER_START_INDEX_IN_CONTAINER_INFORMATION = 1;
	private final static int CONTAINER_STOP_INDEX_IN_CONTAINER_INFORMATION = 2;
	private final static int BITS_PER_NUCLEOTIDE = SimpleNucleotideCodeSequence.BITS_PER_NUCLEOTIDE;
	private final static int BITS_PER_BYTE = 8;

	private final RandomAccessFile genomeFileReader;
	private final Map<String, StartAndStopLocationsInFile> startAndStopLocationsByContainerName;
	private GenomicRangedCoordinate largestContainer;

	public Genome(File genomeFile) throws IOException {
		this.genomeFileReader = new RandomAccessFile(genomeFile, "r");
		startAndStopLocationsByContainerName = new LinkedHashMap<String, StartAndStopLocationsInFile>();
		init();
	}

	private void init() throws IOException {

		// read the last 8 bytes to find the beginning of the container information start
		long fileEnd = genomeFileReader.length();
		genomeFileReader.seek(fileEnd - LONG_LENGTH_IN_BYTES);
		long containerInformationStart = genomeFileReader.readLong();
		int containerInformationSize = (int) (fileEnd - containerInformationStart - LONG_LENGTH_IN_BYTES);
		// read the container information
		genomeFileReader.seek(containerInformationStart);
		byte[] containerInformationAsByteArray = new byte[containerInformationSize];
		genomeFileReader.read(containerInformationAsByteArray);
		String containerInformation = new String(containerInformationAsByteArray);
		// parse the container information
		String[] containerInformationByLine = containerInformation.split(StringUtil.LINUX_NEWLINE);
		for (String containerRow : containerInformationByLine) {
			String[] containerRowByTabs = containerRow.split(StringUtil.TAB);
			String containerName = containerRowByTabs[CONTAINER_NAME_INDEX_IN_CONTAINER_INFORMATION];
			String startIndexAsString = containerRowByTabs[CONTAINER_START_INDEX_IN_CONTAINER_INFORMATION];
			long startIndex = Long.parseLong(startIndexAsString);
			String stopIndexAsString = containerRowByTabs[CONTAINER_STOP_INDEX_IN_CONTAINER_INFORMATION];
			long stopIndex = Long.parseLong(stopIndexAsString);
			StartAndStopLocationsInFile startAndStop = new StartAndStopLocationsInFile(startIndex, stopIndex);
			startAndStopLocationsByContainerName.put(containerName, startAndStop);
		}

		for (GenomicRangedCoordinate container : getContainerSizes()) {
			if (largestContainer == null || container.size() > largestContainer.size()) {
				largestContainer = container;
			}
		}
	}

	public GenomicRangedCoordinate getLargestContainer() {
		return largestContainer;
	}

	public List<GenomicRangedCoordinate> getContainerSizes() {
		List<GenomicRangedCoordinate> containerSizes = new ArrayList<GenomicRangedCoordinate>();
		for (String containerName : getContainerNames()) {
			long start = 1;
			long stop = getContainerSize(containerName);
			containerSizes.add(new GenomicRangedCoordinate(containerName, start, stop));
		}
		return containerSizes;
	}

	public GenomicRangedCoordinate getContainer(String containerName) {
		return new GenomicRangedCoordinate(containerName, 1, getContainerSize(containerName));
	}

	public ISequence getSequence(String containerName, long sequenceStart, long sequenceEnd) throws IOException {
		ISequence sequence = null;
		StartAndStopLocationsInFile startAndStop = startAndStopLocationsByContainerName.get(containerName);

		if (sequenceStart > sequenceEnd) {
			long temp = sequenceStart;
			sequenceStart = sequenceEnd;
			sequenceEnd = temp;
		}

		if (startAndStop != null) {
			// sequenceStart is one based and the sequences are stored zero based
			long offsetFromStartToSequenceInBits = (sequenceStart - 1) * BITS_PER_NUCLEOTIDE;

			long offsetFromStartToFirstByteContainingPartOfSequenceInBytes = offsetFromStartToSequenceInBits / BITS_PER_BYTE;
			int startIndexWithinByteOfSequenceStartInBits = (int) (offsetFromStartToSequenceInBits % BITS_PER_BYTE);
			long firstByteContainingPartOfSequenceInFile = startAndStop.getStartLocationInBytes() + offsetFromStartToFirstByteContainingPartOfSequenceInBytes;

			int sequenceLength = (int) (sequenceEnd - sequenceStart + 1);
			int sequenceLengthInBits = sequenceLength * BITS_PER_NUCLEOTIDE;

			long offsetFromStartToEndOfSequenceInBits = offsetFromStartToSequenceInBits + sequenceLengthInBits;
			long offsetFromStartToLastByteContainingPartOfSequenceInBytes = offsetFromStartToEndOfSequenceInBits / BITS_PER_BYTE;
			long lastByteContainingPartOfSequenceInFile = startAndStop.getStartLocationInBytes() + offsetFromStartToLastByteContainingPartOfSequenceInBytes;

			if (lastByteContainingPartOfSequenceInFile > startAndStop.getStopLocationInBytes()) {
				long containerLengthInNucleotides = (long) (((startAndStop.getStopLocationInBytes() - startAndStop.getStartLocationInBytes()) * BITS_PER_BYTE) / BITS_PER_NUCLEOTIDE);
				throw new IllegalStateException("sequence end[" + sequenceEnd + "] is greater than the length[" + containerLengthInNucleotides + "] of the container[" + containerName + "].");
			}

			int numberOfBytes = (int) (lastByteContainingPartOfSequenceInFile - firstByteContainingPartOfSequenceInFile + 1);
			genomeFileReader.seek(firstByteContainingPartOfSequenceInFile);

			if (numberOfBytes < 0) {
				System.out.println("here");
			}

			byte[] sequenceBytes = new byte[numberOfBytes];
			genomeFileReader.read(sequenceBytes);

			BitSet sequenceWithExtrasAsBits = BitSet.valueOf(sequenceBytes);
			int startInBitSet = startIndexWithinByteOfSequenceStartInBits;
			int stopInBitSet = startInBitSet + sequenceLengthInBits;
			BitSet sequenceAsBits = sequenceWithExtrasAsBits.get(startInBitSet, stopInBitSet);
			sequence = new SimpleNucleotideCodeSequence(sequenceLengthInBits, sequenceAsBits);
		}

		return sequence;
	}

	public Set<String> getContainerNames() {
		return startAndStopLocationsByContainerName.keySet();
	}

	public long getContainerSize(String containerName) {
		long size = 0;

		StartAndStopLocationsInFile startAndStop = startAndStopLocationsByContainerName.get(containerName);
		if (startAndStop != null) {
			size = (long) (((startAndStop.getStopLocationInBytes() - startAndStop.getStartLocationInBytes()) * BITS_PER_BYTE) / BITS_PER_NUCLEOTIDE);
		}

		return size;
	}

	public void close() throws IOException {
		if (genomeFileReader != null) {
			genomeFileReader.close();
		}
	}

	public static void main(String[] args) {
		File genomeFile = new File("D:/kurts_space/sequence/hg19_genome.gnm");
		try {
			Genome genome = new Genome(genomeFile);
			ISequence sequence = getSequenceByProbeId(genome, "chr6:117632028:117632054:+");
			if (sequence != null) {
				System.out.println(sequence);
				System.out.println(sequence.getReverseCompliment());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static ISequence getSequenceByProbeId(Genome genome, String probeId) {
		ISequence sequence = null;
		String[] split = probeId.split(":");
		String sequenceString = split[0];
		String startAsString = split[1];
		long start = Long.parseLong(startAsString);
		String stopAsString = split[2];
		Long stop = Long.parseLong(stopAsString);
		String strand = split[3];
		try {
			sequence = genome.getSequence(sequenceString, start, stop);
			if (strand.equals("-")) {
				sequence = sequence.getReverseCompliment();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sequence;
	}
}