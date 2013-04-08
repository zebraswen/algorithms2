/**
 * Content-aware picture resizing.
 *
 * @author William Schwartz
 */
public class SeamCarver {
	private static final double BORDER_ENERGY = 3 * (255 * 255);
	private Picture pic;

	public SeamCarver(Picture picture) { this.pic = new Picture(picture); }

	// Copy of current picture
	public Picture picture() { return new Picture(pic); }

	// width of current picture
	public int width() { return pic.width(); }

	// height of current picture
	public int height() { return pic.height(); }

	// Find the square color gradient in one dimension
	private int gradient(java.awt.Color a, java.awt.Color b) {
		int red   = a.getRed()   - b.getRed();
		int green = a.getGreen() - b.getGreen();
		int blue  = a.getBlue()  - b.getBlue();
		return red*red + green*green + blue*blue;
	}

	// energy of pixel at column x and row y
	public double energy(int x, int y) {
		if (x < 0 || x >= width() || y < 0 || y > height()){
			String msg = Integer.toString(x) + ", " + Integer.toString(y);
			throw new IndexOutOfBoundsException(msg);
		}
		if (x == 0 || y == 0 || x == width() - 1 || y == height() - 1)
			return BORDER_ENERGY;
		return gradient(pic.get(x - 1, y), pic.get(x + 1, y)) +
		       gradient(pic.get(x, y - 1), pic.get(x, y + 1));
	}

	// sequence of indices for horizontal seam
	public int[] findHorizontalSeam() {
		transpose();
		int[] seam = findVerticalSeam();
		transpose();
		return seam;
	}

	private void transpose() {
		Picture p = new Picture(height(), width());
		for (int col = 0; col < width(); col++)
			for (int row = 0; row < height(); row++)
				p.set(row, col, pic.get(col, row));
		pic = p;
	}

	// sequence of indices for vertical seam
	public int[] findVerticalSeam() {
		int size = width() * height();
		assert size == node(width() - 1, height() - 1) + 1;
		double[] weights = new double[size];
		double[] distTo = new double[size];
		int[] edgeTo = new int[size];
		for (int v = node(0, 0); v < size; v++) {
			if (row(v) == 1)
				distTo[v] = 0.0;
			else
				distTo[v] = Double.POSITIVE_INFINITY;
			edgeTo[v] = -1;
			weights[v] = energy(col(v), row(v));
		}
		for (int v : toporder(size))
			for (int w : adj(v))
				relax(v, w, weights, distTo, edgeTo);
		// Find min weight bottom node
		double min = Double.POSITIVE_INFINITY;
		int argmin = node(0, height() - 1);
		for (int v = node(0, height() - 1); v < size; v++) {
			if (distTo[v] < min) {
				min = distTo[v];
				argmin = v;
			}
		}
		// get path to min weight bottom node
		int[] seam = new int[height()];
		seam[row(argmin)] = col(argmin);
		for (int prev = edgeTo[argmin]; prev >= 0; prev = edgeTo[prev])
			seam[row(prev)] = col(prev);
		return seam;
	}

	private void relax(int from, int to, double[] weights, double[] distTo, int[] edgeTo) {
		int toc = col(to), tor = row(to);
		if (distTo[to] > distTo[from] + weights[to]) {
			distTo[to] = distTo[from] + weights[to];
			edgeTo[to] = from;
		}
	}

	// Return the node IDs in topological order
	private Iterable<Integer> toporder(int size) {
		Stack<Integer> toporder = new Stack<Integer>();
		boolean[] marked = new boolean[size];
		for (int id = 0; id < size; id++)
			if (!marked[id])
				dfs(id, marked, toporder);
		return toporder;
	}

	private void dfs(int v, boolean[] marked, Stack<Integer> toporder) {
		marked[v] = true;
		for (int w : adj(v))
			if (!marked[w])
				dfs(w, marked, toporder);
		toporder.push(v);
	}

	// Mapping between node ID numbers and (col, row) notation. No bounds
	// checking is performed so use with caution.
	private int node(int col, int row) { return row * width() + col; }
	private int col(int node) { return node % width(); }
	private int row(int node) { return node / width(); }
	// Edges point downward to the three neighboring points the row below.
	private Iterable<Integer> adj(int from) {
		if (from < 0 || from >= width() * height())
			throw new IllegalArgumentException(Integer.toString(from));
		Queue<Integer> neighbors = new Queue<Integer>();
		if (row(from) == height() - 1)
			return neighbors;
		if (col(from) > 0)
			neighbors.enqueue(node(col(from) - 1, row(from) + 1));
		neighbors.enqueue(node(col(from), row(from) + 1));
		if (col(from) < width() - 1)
			neighbors.enqueue(node(col(from) + 1, row(from) + 1));
		return neighbors;
	}

	// remove horizontal seam from picture
	public void removeHorizontalSeam(int[] a) {
		if (height() == 0) {
			String msg = "Cannot remove horizontal seam from 0-height picture";
			throw new IllegalArgumentException(msg);
		}
		if (a.length != width()) {
			String msg = "Horizontal seam narrower than width of picture";
			throw new IllegalArgumentException(msg);
		}
		Picture p = new Picture(width(), height() - 1);
		int lastrow = a[0];
		for (int col = 0; col < width(); col++) {
			if (a[col] < lastrow - 1 || a[col] > lastrow + 1)
				throw new IllegalArgumentException("Non-valid seam");
			if (a[col] < 0 || a[col] >= height())
				throw new IndexOutOfBoundsException(Integer.toString(a[col]));
			lastrow = a[col];
			for (int row = 0; row < height() - 1; row++) {
				if (row < lastrow)
					p.set(col, row, pic.get(col, row));
				else
					p.set(col, row, pic.get(col, row + 1));
			}
		}
		pic = p;
	}


	// remove vertical seam from picture
	public void removeVerticalSeam(int[] a) {
		if (width() == 0) {
			String msg = "Cannot remove vertical seam from zero-width picture";
			throw new IllegalArgumentException(msg);
		}
		if (a.length != height()) {
			String msg = "Vertical seam shorter than height of picture";
			throw new IllegalArgumentException(msg);
		}
		Picture p = new Picture(width() - 1, height());
		int lastcol = a[0];
		for (int row = 0; row < height(); row++) {
			if (a[row] < lastcol - 1 || a[row] > lastcol + 1)
				throw new IllegalArgumentException("Non-valid seam");
			if (a[row] < 0 || a[row] >= width())
				throw new IndexOutOfBoundsException(Integer.toString(a[row]));
			lastcol = a[row];
			for (int col = 0; col < width() - 1; col++) {
				if (col < lastcol)
					p.set(col, row, pic.get(col, row));
				else
					p.set(col, row, pic.get(col + 1, row));
			}
		}
		pic = p;
	}

	private void printEWD() {
		System.out.println("***** EdgeWeightedDigraph Topological Order ****");
		EdgeWeightedDigraph g = digraph();
		int count = 0;
		int[][] m = new int[width()][height()];
		for (int node : new Topological(g).order())
			m[col(node)][row(node)] = count++;
		for (int row = 0; row < height(); row++) {
			for (int col = 0; col < width(); col++)
				System.out.printf(" %3d ", m[col][row]);
			System.out.println();
		}
		System.out.println();
	}

	// Construct the EdgeWeightedDigraph the instructors told us not to
	// construct.
	private EdgeWeightedDigraph digraph() {
		EdgeWeightedDigraph g = new EdgeWeightedDigraph(width() * height());
		for (int v = 0; v < width() * height(); v++)
			for (int w : adj(v))
				g.addEdge(new DirectedEdge(v, w, energy(col(w), row(w))));
		return g;
	}

	private void printTopo() {
		int count = 0;
		int[][] m = new int[width()][height()];
		for (int node : toporder(width() * height()))
			m[col(node)][row(node)] = count++;
		System.out.println("*************** TOPOLOGICAL ORDER *************");
		for (int row = 0; row < height(); row++) {
			for (int col = 0; col < width(); col++)
				System.out.printf(" %3d ", m[col][row]);
			System.out.println();
		}
		System.out.println();
	}

	private void printNode2Point() {
		for (int node = node(0, 0); node < width() * height(); node++) {
			System.out.printf("(%d, %d) ", col(node), row(node));
			if (col(node) == width() - 1)
				System.out.println();
		}
		System.out.println();
	}

	private void printEdges() {
		System.out.println("*************** EDGES ****************");
		System.out.printf("   |      %d            ", 0);
		for (int col = 1; col < width() - 1; col++)
			System.out.printf("|        %d              ", col);
		System.out.printf("|       %d           |\n", width() - 1);
		for (int node = node(0, 0); node < width() * height(); node++) {
			if (col(node) == 0)
				System.out.printf("%3d| ", row(node));
			System.out.printf("%3d -> {", node);
			for (int child : adj(node))
				System.out.printf("%3d,", child);
			System.out.print("} | ");
			if (col(node) == width() - 1)
				System.out.println();
		}
		System.out.println();
	}
}
