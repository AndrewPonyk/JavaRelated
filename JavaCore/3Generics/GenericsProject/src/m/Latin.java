package m;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.StringTokenizer;        // package for StringTokenizer

public class Latin
{
   public static void main(String[] args) throws IOException
   {
      InputStreamReader reader = new
                InputStreamReader(System.in);
      BufferedReader console = new BufferedReader(reader);
      String s = console.readLine();
      int n = Integer.parseInt(s);
      s = console.readLine();
      int m = Integer.parseInt(s);
      int[][] array = new int[n][m];
      int i, j;

  //****PART I: reading the input
      for (i=0; i<n; i++)
      {
         s = console.readLine();
         StringTokenizer sTkn = new StringTokenizer(s);
         for (j=0; j<m; j++)
            array[i][j] = Integer.parseInt(sTkn.nextToken());
      }
      printArray(array, n, m);   // print the nXm table

  //****PART II: checking the elements within each row for equality
      boolean latin = true;           
      int[] aux = new int[m+1];  // array for storing the indices of elements

      for (i=0; i<n; i++)        // loop over all rows
      {
         // if all elements in the i-th row are distinct then OK
         for (j=0; j<=m; j++)    // initialize the aux array
            aux[j] = 0;
         for (j=0; j<m; j++)     // loop over the elements of the i-th row
         {
            int k = array[i][j];
            if (k < 0 || k > m)  // check the table entry for the range
              latin = false;
            else if (aux[k] == 0)// check the table entry for repetition
              aux[k] = 1;
            else
              latin = false;
         }
      }

  //****PART III: checking the elements within each column for equality
      for (j=0; j<m; j++)        // loop over all columns
      {
         // if all elements in the j-th column are distinct then OK
         for (i=0; i<=m; i++)    // initialize the aux array
            aux[i] = 0;
         for (i=0; i<n; i++)     // loop over the elements of the j-th column
         {
            int k = array[i][j];
            if (k < 0 || k > m)  // check the table entry for the range
              latin = false;
            else if (aux[k] == 0)// check the table entry for repetition
              aux[k] = 1;
            else
              latin = false;
         }
      }

  //****PART IV: report the result
      if (latin)
       System.out.println("The array is a Latin rectangle");
      else
       System.out.println("The array is NOT a Latin rectangle");
   }

   
   
   
   
   
   
// this method prints all elements of the 2-dim array a[][] with
// n being the number of rows and m being the number of columns 
   public static void printArray(int[][] a, int n, int m)
   {
      for (int i=0; i<n; i++)
      {
        for (int j=0; j<m; j++)
          if (a[i][j] <= 9)
            System.out.print("  " + a[i][j]);
          else
            System.out.print(" " + a[i][j]);
         System.out.println("");
      }
   }
}