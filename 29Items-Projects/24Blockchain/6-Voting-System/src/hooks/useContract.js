import { useState, useEffect } from "react";
import { useWeb3 } from "./useWeb3";

/**
 * Hook for interacting with a smart contract.
 * Loads the contract ABI and creates a Web3 contract instance.
 *
 * @param {object} contractABI - The compiled contract ABI JSON
 * @param {string} contractAddress - Deployed contract address
 * @returns {{ contract, isLoading, error }}
 */
export function useContract(contractABI, contractAddress) {
  const { web3, isConnected } = useWeb3();
  const [contract, setContract] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!web3 || !isConnected || !contractABI || !contractAddress) {
      setIsLoading(false);
      return;
    }

    try {
      const instance = new web3.eth.Contract(contractABI.abi || contractABI, contractAddress);
      setContract(instance);
      setError(null);
    } catch (err) {
      setError(`Failed to load contract: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  }, [web3, isConnected, contractABI, contractAddress]);

  return { contract, isLoading, error };
}

export default useContract;
