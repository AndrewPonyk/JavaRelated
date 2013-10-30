package jobs;

import play.jobs.Job;

public class GeneratePDFJob extends Job<Integer> {
	
	@Override
	public Integer doJobWithResult() throws Exception {
		Integer result=1;
		for(int i=0;i<3000004;i++){
			int k=i;
			k=k*2;
			result=k++;
		}
		return result;
	}
}
