package com.etheller.warsmash.viewer5.handlers.mdx;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.LayeredDataSource;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAnimatedObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGenericObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer;
import com.hiveworkshop.rms.parsers.mdlx.MdlxMaterial;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2;
import com.hiveworkshop.rms.parsers.mdlx.MdlxRibbonEmitter;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTextureAnimation;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;

/**
 * The effects of the Reforged graphics packages are mostly "popcorn" emitters: they name a PopcornFX effect file
 * ({@code CORN} chunk) that this engine cannot play, so a model with only those draws nothing. The Classic data has
 * the same model with particle emitters, ribbons and event objects, which are drawn instead: a model without any
 * geometry is replaced by the Classic one, and a model with geometry (a doodad, a building, a unit) keeps its own
 * meshes and takes the Classic emitters, attached to the bones of the same name.
 */
final class ClassicEffects {
	private ClassicEffects() {
	}

	static MdlxModel resolve(final ModelViewer viewer, final String path, final MdlxModel model) {
		if ((model.getVersion() <= 800) || model.particleEmittersPopcorn.isEmpty() || (path == null)
				|| path.isEmpty()) {
			return model;
		}
		final LayeredDataSource layered = findLayered(viewer.dataSource);
		if (layered == null) {
			return model;
		}
		final MdlxModel classic;
		try {
			final ByteBuffer buffer = layered.readClassic(path);
			if (buffer == null) {
				return model;
			}
			classic = new MdlxModel(buffer);
		}
		catch (final Exception e) {
			Log.warn("Classic version of " + path + " not read: " + e);
			return model;
		}
		if (model.geosets.isEmpty()) {
			return classic;
		}
		graft(model, classic);
		return model;
	}

	private static LayeredDataSource findLayered(final DataSource data) {
		if (data instanceof LayeredDataSource) {
			return (LayeredDataSource) data;
		}
		if (data instanceof CompoundDataSource) {
			for (final DataSource source : ((CompoundDataSource) data).getSources()) {
				final LayeredDataSource found = findLayered(source);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Adds the particle emitters and ribbons of the Classic model to the model. */
	private static void graft(final MdlxModel model, final MdlxModel classic) {
		if (classic.particleEmitters.isEmpty() && classic.particleEmitters2.isEmpty()
				&& classic.ribbonEmitters.isEmpty()) {
			return;
		}
		final List<MdlxGenericObject> nodes = new ArrayList<>();
		nodes.addAll(model.bones);
		nodes.addAll(model.lights);
		nodes.addAll(model.helpers);
		nodes.addAll(model.attachments);
		nodes.addAll(model.particleEmitters);
		nodes.addAll(model.particleEmitters2);
		nodes.addAll(model.particleEmittersPopcorn);
		nodes.addAll(model.ribbonEmitters);
		nodes.addAll(model.eventObjects);
		nodes.addAll(model.collisionShapes);
		int nextId = nodes.size();
		for (final MdlxGenericObject node : nodes) {
			nextId = Math.max(nextId, node.objectId + 1);
		}
		final Map<String, Integer> idByName = new HashMap<>();
		for (final List<? extends MdlxGenericObject> group : java.util.Arrays.asList(model.bones, model.helpers,
				model.attachments)) {
			for (final MdlxGenericObject node : group) {
				idByName.putIfAbsent(node.name.toLowerCase(Locale.ROOT), node.objectId);
			}
		}
		final List<MdlxGenericObject> classicNodes = new ArrayList<>();
		classicNodes.addAll(classic.bones);
		classicNodes.addAll(classic.lights);
		classicNodes.addAll(classic.helpers);
		classicNodes.addAll(classic.attachments);
		classicNodes.addAll(classic.particleEmitters);
		classicNodes.addAll(classic.particleEmitters2);
		classicNodes.addAll(classic.ribbonEmitters);
		classicNodes.addAll(classic.eventObjects);
		classicNodes.addAll(classic.collisionShapes);
		final Map<Integer, MdlxGenericObject> classicById = new HashMap<>();
		for (final MdlxGenericObject node : classicNodes) {
			classicById.put(node.objectId, node);
		}

		final SequenceMap sequences = new SequenceMap(classic.sequences, model.sequences, model.globalSequences.size());
		final int textureBase = model.textures.size();
		model.textures.addAll(classic.textures);
		model.globalSequences.addAll(classic.globalSequences);
		final int textureAnimationBase = model.textureAnimations.size();
		for (final MdlxTextureAnimation textureAnimation : classic.textureAnimations) {
			sequences.remap(textureAnimation);
			model.textureAnimations.add(textureAnimation);
		}
		final int materialBase = model.materials.size();
		for (final MdlxMaterial material : classic.materials) {
			for (final MdlxLayer layer : material.layers) {
				if (layer.textureId >= 0) {
					layer.textureId += textureBase;
				}
				if (layer.textureAnimationId >= 0) {
					layer.textureAnimationId += textureAnimationBase;
				}
				sequences.remap(layer);
			}
			model.materials.add(material);
		}

		while (model.pivotPoints.size() < nextId) {
			model.pivotPoints.add(new float[3]);
		}
		for (final MdlxParticleEmitter emitter : classic.particleEmitters) {
			place(model, emitter, nextId++, classic, classicById, idByName, sequences);
			model.particleEmitters.add(emitter);
		}
		for (final MdlxParticleEmitter2 emitter : classic.particleEmitters2) {
			place(model, emitter, nextId++, classic, classicById, idByName, sequences);
			if (emitter.replaceableId == 0) {
				emitter.textureId += textureBase;
			}
			model.particleEmitters2.add(emitter);
		}
		for (final MdlxRibbonEmitter emitter : classic.ribbonEmitters) {
			place(model, emitter, nextId++, classic, classicById, idByName, sequences);
			emitter.materialId += materialBase;
			model.ribbonEmitters.add(emitter);
		}
	}

	private static void place(final MdlxModel model, final MdlxGenericObject emitter, final int id,
			final MdlxModel classic, final Map<Integer, MdlxGenericObject> classicById,
			final Map<String, Integer> idByName, final SequenceMap sequences) {
		final int classicId = emitter.objectId;
		int parent = emitter.parentId;
		// The nearest bone up the Classic chain that the model has by name.
		int parentId = -1;
		while ((parent != -1) && (parent != classicId)) {
			final MdlxGenericObject node = classicById.get(parent);
			if (node == null) {
				break;
			}
			final Integer found = idByName.get(node.name.toLowerCase(Locale.ROOT));
			if (found != null) {
				parentId = found;
				break;
			}
			parent = node.parentId;
		}
		emitter.objectId = id;
		emitter.parentId = parentId;
		if (classicId < classic.pivotPoints.size()) {
			model.pivotPoints.add(classic.pivotPoints.get(classicId));
		}
		else {
			model.pivotPoints.add(new float[3]);
		}
		sequences.remap(emitter);
	}

	/**
	 * Moves the key frames of the Classic model into the sequences of the model that have the same names; the
	 * sequences of the two models are not the same length.
	 */
	private static final class SequenceMap {
		private final List<MdlxSequence> from;
		private final MdlxSequence[] to;
		private final int globalSequenceBase;

		SequenceMap(final List<MdlxSequence> from, final List<MdlxSequence> to, final int globalSequenceBase) {
			this.from = from;
			this.globalSequenceBase = globalSequenceBase;
			this.to = new MdlxSequence[from.size()];
			final Map<String, MdlxSequence> byName = new HashMap<>();
			final Map<String, MdlxSequence> byBaseName = new HashMap<>();
			for (final MdlxSequence sequence : to) {
				final String name = sequence.name.toLowerCase(Locale.ROOT);
				byName.putIfAbsent(name, sequence);
				byBaseName.putIfAbsent(baseName(name), sequence);
			}
			for (int i = 0; i < this.to.length; i++) {
				final String name = from.get(i).name.toLowerCase(Locale.ROOT);
				final MdlxSequence match = byName.get(name);
				this.to[i] = match != null ? match : byBaseName.get(baseName(name));
			}
		}

		private static String baseName(final String name) {
			final int dash = name.indexOf(" - ");
			return (dash == -1 ? name : name.substring(0, dash)).trim();
		}

		void remap(final MdlxAnimatedObject object) {
			for (final Iterator<MdlxTimeline<?>> iterator = object.timelines.iterator(); iterator.hasNext();) {
				final MdlxTimeline<?> timeline = iterator.next();
				if (timeline.globalSequenceId >= 0) {
					timeline.globalSequenceId += this.globalSequenceBase;
					continue;
				}
				if (!this.<Object>remapAny(timeline)) {
					iterator.remove();
				}
			}
		}

		@SuppressWarnings("unchecked")
		private <T> boolean remapAny(final MdlxTimeline<?> timeline) {
			return remap((MdlxTimeline<T>) timeline);
		}

		/** Whether any key frame is left. */
		private <T> boolean remap(final MdlxTimeline<T> timeline) {
			final int count = timeline.frames.length;
			final long[] frames = new long[count];
			final int[] kept = new int[count];
			int keptCount = 0;
			for (int i = 0; i < count; i++) {
				for (int s = 0; s < this.to.length; s++) {
					final long[] interval = this.from.get(s).interval;
					if ((timeline.frames[i] >= interval[0]) && (timeline.frames[i] <= interval[1])) {
						final MdlxSequence target = this.to[s];
						final long offset = timeline.frames[i] - interval[0];
						if ((target != null) && (offset <= (target.interval[1] - target.interval[0]))) {
							frames[keptCount] = target.interval[0] + offset;
							kept[keptCount++] = i;
						}
						break;
					}
				}
			}
			if (keptCount == 0) {
				return false;
			}
			timeline.frames = java.util.Arrays.copyOf(frames, keptCount);
			timeline.values = pick(timeline.values, kept, keptCount);
			timeline.inTans = pick(timeline.inTans, kept, keptCount);
			timeline.outTans = pick(timeline.outTans, kept, keptCount);
			return true;
		}

		@SuppressWarnings("unchecked")
		private static <T> T[] pick(final T[] source, final int[] kept, final int keptCount) {
			if (source == null) {
				return null;
			}
			final T[] result = (T[]) Array.newInstance(source.getClass().getComponentType(), keptCount);
			for (int i = 0; i < keptCount; i++) {
				result[i] = source[kept[i]];
			}
			return result;
		}
	}
}
