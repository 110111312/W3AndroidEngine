package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

public abstract class GenericNode {

	public Vector3 pivot;
	public Vector3 localLocation;
	public Quaternion localRotation;
	public Vector3 localScale;
	public Vector3 worldLocation;
	public Quaternion worldRotation;
	public Vector3 worldScale;
	public Matrix4 localMatrix;
	public Matrix4 worldMatrix;
	public GenericNode parent;
	public List<Node> children;
	public boolean dontInheritTranslation;
	public boolean dontInheritRotation;
	public boolean dontInheritScaling;
	public boolean visible;
	public boolean wasDirty;
	public boolean dirty;

	/** The inverse of the world rotation, worked out when asked for rather than kept for every node. */
	public final Quaternion getInverseWorldRotation(final Quaternion out) {
		out.x = -this.worldRotation.x;
		out.y = -this.worldRotation.y;
		out.z = -this.worldRotation.z;
		out.w = this.worldRotation.w;
		return out;
	}

	public final Vector3 getInverseWorldScale(final Vector3 out) {
		out.x = 1 / this.worldScale.x;
		out.y = 1 / this.worldScale.y;
		out.z = 1 / this.worldScale.z;
		return out;
	}

	/** Null until the first child; the nodes of most model instances never get one. */
	public void addChild(final Node child) {
		if (this.children == null) {
			this.children = new ArrayList<>(2);
		}
		this.children.add(child);
	}

	public void removeChild(final Node child) {
		if (this.children != null) {
			this.children.remove(child);
		}
	}

	protected abstract void update(float dt, Scene scene);
}
