package javautilconcurent.forkjoin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class MatrixMultiplyingForkJoin {

	public static void main(String[] args) {
		// Matrix multiplying without Fork Join Framework 
		/*	Matrix a = new Matrix(2, 2);
		a.setValues("1,2;3,4");
		
		Matrix b = new Matrix(2, 3);
		b.setValues("1,1,1;2,2,2");
		
		System.out.println(Matrix.multiply(a, b)); // Result : 5,5,5;11,11,11
*/	
	
		
		
		ForkJoinPool pool = new ForkJoinPool();
		
		Matrix a = new Matrix(2, 2);
		a.setValues("1,2;3,4");
		
		Matrix b = new Matrix(2, 3);
		b.setValues("1,1,1;2,2,2");
		
		Matrix c = new Matrix(a.getRows(), b.getCols());
		
		pool.invoke(new MatMult(a, b, c));
		
		System.out.println(c); 
		
	}
}


//ForkJoin Class
class MatMult extends RecursiveAction{
	private Matrix a, b, c;
	private int row;

	MatMult(Matrix a, Matrix b, Matrix c) {
		this(a, b, c, -1);
	}
	
	MatMult(Matrix a, Matrix b, Matrix c, int row) {
		if (a.getCols() != b.getRows())
			throw new IllegalArgumentException("rows/columns mismatch");
		this.a = a;
		this.b = b;
		this.c = c;
		this.row = row;
	}
	
	@Override
	protected void compute() {
		if(row == -1){
			List<MatMult> tasks = new ArrayList<>();
			for (int row = 0; row < a.getRows(); row++)
				tasks.add(new MatMult(a, b, c, row));
			invokeAll(tasks);
		}else {
			multiplyRowByColumn(a, b, c, row);
		}
	}
	
	static void multiplyRowByColumn(Matrix a, Matrix b, Matrix c, int row) {
		for (int j = 0; j < b.getCols(); j++)
			for (int k = 0; k < a.getCols(); k++)
				c.setValue(row,j,c.getValue(row, j) + a.getValue(row, k) * b.getValue(k, j));
	}
	
}


class Matrix {
	private double[][] matrix;

	Matrix(int nrows, int ncols) {
		matrix = new double[nrows][ncols];
	}
	
	int getCols() {
		return matrix[0].length;
	}
	
	int getRows() {
		return matrix.length;
	}
	
	double getValue(int row, int col) {
		return matrix[row][col];
	}
	
	void setValue(int row, int col, double value) {
		matrix[row][col] = value;
	}
	
	void setValues(String val) {
		val = val.replaceAll(" ", "");
		
		String[] rows = val.split(";");
		double[][] mas = new double[rows.length][rows[0].split(",").length];
		
		for (int i = 0; i < rows.length; i++) {
			String[] columns = rows[i].split(",");
			for(int j=0;j<columns.length;j++){
				mas[i][j] = Double.parseDouble(columns[j]);
			}
		}
		
		this.matrix = mas;
	}
	
	@Override
	public String toString() {
		String result = "";
		for (int i = 0; i < this.matrix.length; i++) {
			for (int j = 0; j < this.matrix[i].length; j++) {
				result += this.matrix[i][j] + " ";
			}
			result += "\n";
		}

		return result;
	}
	
	static Matrix multiply(Matrix a, Matrix b){
		if(a.getCols() != b.getRows())
			throw new IllegalArgumentException("Wrong matrixes : a.getCols() should be equal to b.getRows()");
		
		Matrix result = new Matrix(a.getRows(), b.getCols());
		
		for (int i = 0; i < a.getRows(); i++)
			for (int j = 0; j < b.getCols(); j++){
				for (int k = 0; k < a.getCols(); k++)
					result.setValue(i, j, result.getValue(i, j)+a.getValue(i, k)*
							b.getValue(k, j));
			}
		
		return result;
	}
}