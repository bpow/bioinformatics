/**
 *   Copyright 2013 Roche NimbleGen
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.roche.heatseq.objects;

import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMRecord;

import com.roche.heatseq.process.FastqAndBamFileMerger;
import com.roche.heatseq.utils.BamFileUtil;
import com.roche.heatseq.utils.SAMRecordUtil;
import com.roche.sequencing.bioinformatics.common.sequence.ISequence;

/**
 * 
 * Standard ReadPair
 * 
 */
public class ReadPair implements IReadPair {
	private final SAMRecord record;
	private final SAMRecord mate;
	private final String uid;
	private final ISequence captureTargetSequence;
	private final String probeId;

	public ReadPair(SAMRecord record, SAMRecord mate, String uid, ISequence captureTargetSequence, String probeId) {
		super();
		this.record = record;
		this.mate = mate;
		this.uid = uid;
		this.captureTargetSequence = captureTargetSequence;
		this.probeId = probeId;
	}

	@Override
	public String getReadName() {
		return record.getReadName();
	}

	@Override
	public String getUid() {
		return uid;
	}

	@Override
	public SAMRecord getRecord() {
		return record;
	}

	@Override
	public SAMRecord getMateRecord() {
		return mate;
	}

	@Override
	public short getSequenceOneQualityScore() {
		return BamFileUtil.getQualityScore(getSequenceOneQualityString());
	}

	@Override
	public short getSequenceTwoQualityScore() {
		return BamFileUtil.getQualityScore(getSequenceTwoQualityString());
	}

	@Override
	public short getTotalSequenceQualityScore() {
		return (short) (getSequenceOneQualityScore() + getSequenceTwoQualityScore());
	}

	@Override
	public String getSequenceOne() {
		return FastqAndBamFileMerger.getReadSequenceFromMergedRecord(record);
	}

	@Override
	public String getSequenceTwo() {
		return FastqAndBamFileMerger.getReadSequenceFromMergedRecord(mate);
	}

	@Override
	public String getSequenceOneQualityString() {
		return FastqAndBamFileMerger.getReadBaseQualityFromMergedRecord(record);
	}

	@Override
	public String getSequenceTwoQualityString() {
		return FastqAndBamFileMerger.getReadBaseQualityFromMergedRecord(mate);
	}

	@Override
	public String getReadGroup() {
		String readGroup = (String) record.getAttribute(SAMRecordUtil.READ_GROUP_ATTRIBUTE_TAG);
		return readGroup;
	}

	@Override
	public SAMFileHeader getSamHeader() {
		return record.getHeader();
	}

	@Override
	public String getSequenceName() {
		return record.getReferenceName();
	}

	@Override
	public int getOneMappingQuality() {
		return record.getMappingQuality();
	}

	@Override
	public int getTwoMappingQuality() {
		return mate.getMappingQuality();
	}

	@Override
	public void markAsDuplicate() {
		record.setDuplicateReadFlag(true);
		mate.setDuplicateReadFlag(true);
	}

	@Override
	public ISequence getCaptureTargetSequence() {
		return captureTargetSequence;
	}

	@Override
	public String getProbeId() {
		return probeId;
	}

}
