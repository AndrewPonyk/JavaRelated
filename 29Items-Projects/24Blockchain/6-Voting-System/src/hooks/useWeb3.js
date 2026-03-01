import { useWeb3Context } from "../contexts/Web3Context";

export function useWeb3() {
  return useWeb3Context();
}

export default useWeb3;
